package com.purify.purifyaiagent.profile;

import com.purify.purifyaiagent.model.ActivityLevel;
import com.purify.purifyaiagent.model.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户画像的读写，落在 MySQL 的 {@code user_profile} / {@code user_profile_weight_history} 两张表。
 *
 * <p>这里只做存取，<b>不做业务判断</b>：合并新旧画像、判断体重有没有变化该不该记一笔，
 * 都在 {@code UserProfileTool} 里。这样这个类里的每个方法都对应一条 SQL，
 * 出问题只要看是哪条语句，不用顺着一堆 if 去猜。
 *
 * <p>SQL 用到的表由 {@code spring.sql.init} 在启动时按
 * {@code db/user-profile-schema-mysql.sql} 建好，所以这里不做任何建表或存在性检查。
 *
 * <p><b>关于 {@code userId}</b>：它是 {@code user.id} 的十进制字符串形式
 * （不是 {@code Long}）。表的 {@code user_id} 列是 {@code VARCHAR(64)}，
 * 而它在接入登录之前就建好、还存着按浏览器 UUID 记的旧数据了——
 * MySQL 8 没有 {@code MODIFY COLUMN IF ...}，改列类型要走 Java 侧的 schema 守卫，收益为零。
 * 19 位的数字放进 64 字符里绰绰有余，而这个类本来就是按字符串比较的。
 *
 * <p>接入登录之前存下的那些行（key 是 UUID 或 {@code "anonymous"}）从此读不到了，
 * 但一条都没删——清理 SQL 以注释形式放在建表脚本的头部，要不要执行由人决定。
 */
@Slf4j
public class UserProfileRepository {

    /**
     * 喂给<b>模型</b>的体重流水条数上限。
     *
     * <p>10 条够看出趋势了。带全部流水没意义：模型要的是「在降还是在涨」，
     * 不是每一笔明细，而明细每多一条就多占一份 token。
     *
     * <p><b>这个数只管模型那一侧，不要拿它去限制页面。</b>它本来是被
     * {@code findWeightHistory} 用在所有调用方身上的，而体重变化页要画的是
     * 「这几个月的趋势」——一条最多 10 个点的曲线，等于看不到趋势。
     * 两个消费者的约束是相反的（一边省 token，一边要尽量多的点），
     * 所以现在是两个方法：{@link #findRecentWeightHistory} 给模型，
     * {@link #findWeightHistory} 给页面。
     */
    private static final int MODEL_HISTORY_LIMIT = 10;

    /**
     * 给<b>页面</b>的体重流水条数上限。
     *
     * <p>不是「分页」而是「兜底」：一个人一年天天称也就 365 条，
     * 而这个上限只用来挡住「表里被灌了几十万行、一次全查出来」这种极端情况。
     * 真要有人攒到 500 条以上，那时候该做的是按时间窗查，而不是把上限调大。
     */
    private static final int PAGE_HISTORY_LIMIT = 500;

    private final JdbcTemplate jdbcTemplate;

    public UserProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 读画像。没建过档的用户返回 {@link Optional#empty()}，而不是一份空画像——两者含义不同。 */
    public Optional<UserProfile> find(String userId) {
        List<UserProfile> found = jdbcTemplate.query("""
                SELECT age, height_cm, weight_kg, goal, activity_level,
                       diet_preference, avoid_food, updated_at
                  FROM user_profile
                 WHERE user_id = ?
                """, (resultSet, rowNum) -> new UserProfile(
                // 这几列都可空，所以不能用 getInt / getDouble——它们把 NULL 读成 0，
                // 于是「没填身高」会变成「身高 0」，再往下 BMI 就废了
                resultSet.getObject("age", Integer.class),
                resultSet.getObject("height_cm", Double.class),
                resultSet.getObject("weight_kg", Double.class),
                resultSet.getString("goal"),
                ActivityLevel.parse(resultSet.getString("activity_level")),
                resultSet.getString("diet_preference"),
                resultSet.getString("avoid_food"),
                resultSet.getObject("updated_at", LocalDateTime.class)), userId);

        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /**
     * 整行写入画像，没有就新建。
     *
     * <p>用「{@code INSERT IGNORE} 占位 + {@code UPDATE} 赋值」两步，而不是
     * {@code INSERT ... ON DUPLICATE KEY UPDATE}：后者要靠 {@code VALUES()} 引用新值，
     * 而 {@code VALUES()} 在 MySQL 8.0.20 之后已经标记废弃，每次执行都往日志里吐一条警告；
     * 换成新的行别名写法又要求 8.0.19 以上。两步走没有版本要求，也不产生警告。
     *
     * <p>{@code INSERT IGNORE} 在这里只保证「这一行存在」，业务字段全交给 {@code UPDATE}——
     * 于是不管用户是第一次建档还是第 N 次更新，走的都是同一条语句。
     *
     * <p>两步之间没有事务：并发写同一个用户时理论上有极小概率互相覆盖，
     * 但这个场景是「一个人跟自己的一个会话说话」，不值得为它引入事务。
     *
     * @param profile 已经合并好的完整画像，不是增量
     */
    public void save(String userId, UserProfile profile) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT IGNORE INTO user_profile (user_id, created_at, updated_at) VALUES (?, ?, ?)",
                userId, now, now);

        int updated = jdbcTemplate.update("""
                UPDATE user_profile
                   SET age = ?, height_cm = ?, weight_kg = ?, goal = ?,
                       activity_level = ?, diet_preference = ?, avoid_food = ?, updated_at = ?
                 WHERE user_id = ?
                """,
                profile.age(),
                profile.heightCm(),
                profile.weightKg(),
                profile.goal(),
                profile.activityLevel() == null ? null : profile.activityLevel().name(),
                profile.dietPreference(),
                profile.avoidFood(),
                now,
                userId);
        log.debug("[UserProfile] 已写入画像：userId={} 影响 {} 行", userId, updated);
    }

    /**
     * 最近 10 条体重流水（新的在前）——<b>给模型看的窗口</b>，条数见
     * {@link #MODEL_HISTORY_LIMIT}。
     *
     * <p>不给 limit 参数是有意的：那个数只该由这个类决定（一边省 token、
     * 一边要尽量多的点），让调用方传的话，早晚会出现
     * 「模型那条路径传了 50，报告却说 10」这种没人看得出来的不一致。
     */
    public List<UserProfile.WeightRecord> findRecentWeightHistory(String userId) {
        return queryWeightHistory(userId, MODEL_HISTORY_LIMIT);
    }

    /**
     * 最新的一条流水。没有就是空。
     *
     * <p>给两处判断用：「这次记的体重和上一条是不是同一天/同一个值」，
     * 以及「删掉的这条是不是最新那条」。都只需要一条，不该把整份流水拉出来。
     */
    public Optional<UserProfile.WeightRecord> findLatestWeightRecord(String userId) {
        return queryWeightHistory(userId, 1).stream().findFirst();
    }

    /**
     * 全部体重流水（新的在前）——<b>给页面看的</b>。
     *
     * <p>上限见 {@link #PAGE_HISTORY_LIMIT}，它只是兜底，不是分页。
     */
    public List<UserProfile.WeightRecord> findWeightHistory(String userId) {
        return queryWeightHistory(userId, PAGE_HISTORY_LIMIT);
    }

    /**
     * 按主键删掉一条体重流水。
     *
     * <p>表本身仍然是「只追加」的（见 {@link #appendWeight}）：这里删的是**记错的那一条**，
     * 不是「改历史」。没有这个口子的话，手滑打进一个 720，那条线会永远钉在图上，
     * 还会把整张图的纵轴拉爆——而用户没有任何办法把它去掉。
     *
     * <p>{@code user_id} 一起进 WHERE，不是多余的：{@code id} 是全局自增的，
     * 只按 id 删就给了「删别人的记录」一个入口。带上它之后，删不到就是删不到。
     *
     * @return 真的删掉了几行（0 表示这条不属于这个用户，或者已经没了）
     */
    public int deleteWeightRecord(String userId, Long id) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM user_profile_weight_history WHERE user_id = ? AND id = ?", userId, id);
        log.info("[UserProfile] userId={} 删除体重流水 id={}，影响 {} 行", userId, id, deleted);
        return deleted;
    }

    /**
     * 读流水。字段顺序要和 {@link UserProfile.WeightRecord} 的构造器对齐，
     * 两个公开方法都走这里，免得两处 SELECT 慢慢不一致。
     */
    private List<UserProfile.WeightRecord> queryWeightHistory(String userId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, weight_kg, recorded_at
                  FROM user_profile_weight_history
                 WHERE user_id = ?
                 ORDER BY recorded_at DESC, id DESC
                 LIMIT ?
                """, (resultSet, rowNum) -> new UserProfile.WeightRecord(
                resultSet.getLong("id"),
                resultSet.getDouble("weight_kg"),
                resultSet.getObject("recorded_at", LocalDateTime.class)), userId, limit);
    }

    /**
     * 往体重流水里追加一条。
     *
     * <p>这张表只增不改：删掉中间某一条，趋势就断了，也说不清当初记的是多少。
     * 「该不该记」由调用方判断，这里不查重。
     */
    public void appendWeight(String userId, double weightKg, LocalDateTime recordedAt) {
        jdbcTemplate.update("""
                INSERT INTO user_profile_weight_history (user_id, weight_kg, recorded_at)
                VALUES (?, ?, ?)
                """, userId, weightKg, recordedAt);
    }

    /**
     * 删掉某个用户的画像和全部流水。
     *
     * <p>给「忘记我」这类需求留的口子，同时也是测试用例的收尾手段：
     * 用例自己造的数据自己删，不给正式库留垃圾。
     */
    public void delete(String userId) {
        int profiles = jdbcTemplate.update("DELETE FROM user_profile WHERE user_id = ?", userId);
        int weights = jdbcTemplate.update("DELETE FROM user_profile_weight_history WHERE user_id = ?", userId);
        log.info("[UserProfile] 已删除用户数据：userId={} 画像 {} 行、体重流水 {} 行", userId, profiles, weights);
    }
}
