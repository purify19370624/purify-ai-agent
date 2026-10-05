package com.purify.purifyaiagent.profile;

import com.purify.purifyaiagent.exception.ApiException;
import com.purify.purifyaiagent.model.ProfileChange;
import com.purify.purifyaiagent.model.ProfileField;
import com.purify.purifyaiagent.model.UserProfile;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 用户画像的读写。
 *
 * <p><b>抽出来的理由不是「少写几行」，是两条写入路径必须共用同一套落库规则。</b>
 * 画像有两个入口：对话里的 {@code UserProfileTool}（模型听到什么就存什么），
 * 和设置页的 {@code ProfileController}（用户自己填表）。如果各写一份，很快就会出现
 * 「从设置页改的体重不进体重流水」这类问题——而且不会有任何报错。
 *
 * <p><b>但两边的合并语义是不一样的，这一点必须分清：</b>
 * <ul>
 *   <li>{@link #update} —— <b>合并</b>。给对话用。模型每轮只会听到一两个字段，
 *       没提的必须保持原值，所以走 {@link UserProfile#merge} 的「非空覆盖」；</li>
 *   <li>{@link #replace} —— <b>整体替换</b>。给设置页用。表单上显示的就是全部内容，
 *       保存时写回去的就该是全部内容。</li>
 * </ul>
 * 为什么设置页不能也用合并：合并把「空」当成「没填」，于是<b>用户永远清不掉一个字段</b>。
 * 忌口填错了想删掉，勾掉再保存，回来一看还在——因为空值被当成了「这次没提」。
 * 整体替换没有这个问题：表单上是什么，库里就是什么。
 *
 * <p>两条路径共用的部分是 {@link #persist}：盖时间戳、写库、按需追加体重流水。
 * 「体重真的变了才记流水」这条规则只写一遍。
 */
@Slf4j
public class ProfileService {

    /**
     * 体重能接受的范围（公斤）。
     *
     * <p>宽得离谱是有意的：它拦的是「手滑多打一位」和「模型听错数」，
     * 不是替医生判断谁该多重。写得窄一点（比如 40–150）会开始误伤真实用户，
     * 而那种误伤的现场是「我的体重存不进去」，比放进一条脏数据更难解释。
     */
    private static final double MIN_WEIGHT_KG = 20;

    private static final double MAX_WEIGHT_KG = 400;

    private final UserProfileRepository repository;

    public ProfileService(UserProfileRepository repository) {
        this.repository = repository;
    }

    /** 读画像。库里没有这个用户时返回一份空画像，而不是 null——调用方不用到处判空。 */
    public UserProfile read(String userId) {
        return repository.find(userId).orElseGet(UserProfile::empty);
    }

    /**
     * 全部体重流水，新的在前。<b>给页面看的。</b>
     *
     * <p>和 {@link #recentHistory} 分成两个方法，是因为两个消费者的要求是相反的：
     * 页面要「尽量多的点」才画得出趋势，模型要「尽量少的点」才不浪费 token。
     * 原先只有一个 {@code history()}，条数上限是按模型定的 10——
     * 于是体重变化那页永远只有 10 个点，而那正是这个功能最要命的一处。
     */
    public List<UserProfile.WeightRecord> history(String userId) {
        return repository.findWeightHistory(userId);
    }

    /**
     * 最近 10 条体重流水，新的在前。<b>给模型看的窗口。</b>
     *
     * <p>条数由仓储决定（{@code UserProfileRepository.MODEL_HISTORY_LIMIT}），
     * 这里不重复写一个数字——两处各写一个的话，那个数字早晚会不一致，
     * 而表现只是「模型看到的历史忽然变多变少」，谁都注意不到。
     */
    public List<UserProfile.WeightRecord> recentHistory(String userId) {
        return repository.findRecentWeightHistory(userId);
    }

    /**
     * 一次合并写入的结果：写入后的画像 + 这次真正被改掉的字段。
     *
     * <p>改动列表由 {@link #update} 一起返回，而不是让调用方自己比：
     * {@code existing} 和 {@code merged} 只在这个方法里同时存在过。
     *
     * @param profile 写入后的完整画像
     * @param changes 这次真的变了的字段，没有改动就是空表
     */
    public record UpdateResult(UserProfile profile, List<ProfileChange> changes) {
    }

    /**
     * 合并写入，返回合并后的完整画像和这次的改动。
     *
     * <p>给对话用：只覆盖 {@code incoming} 里非空的那几个字段。
     *
     * @param incoming 这次要改的字段，没提到的传 null
     * @throws IllegalArgumentException 一个字段都没传。工具那条路径会先自己判掉，
     *                                  这里的异常是给「将来有人加了新调用点却忘了判」兜底的
     */
    public UpdateResult update(String userId, UserProfile incoming) {
        if (incoming == null || incoming.isBlank()) {
            throw new IllegalArgumentException("没有传来任何可以保存的字段");
        }
        UserProfile existing = read(userId);
        UserProfile merged = existing.merge(incoming);
        // 先算差异再落库：persist 只多盖一个时间戳，两件事互不影响。
        // 但判断必须用同一对 existing/merged——拿落库后的画像去比就多了一层
        // 「时间戳也被算成改动」的可能
        List<ProfileChange> changes = diff(existing, merged);
        return new UpdateResult(persist(userId, merged, existing.weightKg()), changes);
    }

    /**
     * 整体替换，返回写入后的画像。
     *
     * <p>给设置页用：{@code incoming} 里 null 的字段就是「清空」，不是「没填」。
     */
    public UserProfile replace(String userId, UserProfile incoming) {
        UserProfile existing = read(userId);
        return persist(userId, incoming, existing.weightKg());
    }

    /**
     * 记一次体重 —— 体重变化页上那个「记一笔」。<b>只动体重这一项，其余字段原样保留。</b>
     *
     * <p><b>为什么单独开一个方法，而不是让页面也去调 {@link #replace}。</b>
     * 一个是整体替换（表单上是全部内容）、一个是「我今天称了」，语义差得远：
     * 让页面走 replace，前端就得先把七个字段读出来再原样发回去——任何一次读失败或者
     * 用户同时在另一台设备上改了忌口，都会被这次覆盖写掉。而语义上用户只说了体重。
     *
     * <p>和 {@link #update}（对话那条路径，模型听到什么存什么）也不共用：
     * 那条走的是「非空才覆盖」的合并，这里要的是「一定写进体重这一项」，
     * 而且要不要记流水这一条，两边的前提也不一样（见 {@link #shouldAppendWeight}）。
     *
     * @param weightKg 这次称出来的体重。范围校验在 {@link #persist} 里，三条路径共用
     */
    public UserProfile recordWeight(String userId, Double weightKg) {
        if (weightKg == null) {
            throw ApiException.invalidProfile("error.profile.weightRequired");
        }
        LocalDateTime now = LocalDateTime.now();
        UserProfile existing = read(userId);
        UserProfile updated = new UserProfile(
                existing.age(), existing.heightCm(), weightKg, existing.goal(),
                existing.activityLevel(), existing.dietPreference(), existing.avoidFood(), now);

        // 走自己的落库，不走 persist：persist 的记账规则是「和上次不一样才记一笔」，
        // 那是给「顺带改了体重」的场景用的；而这里是用户专门来记一次体重，
        // 规则不一样，见 shouldAppendWeight
        requireSaneWeight(updated.weightKg());
        repository.save(userId, updated);
        if (shouldAppendWeight(userId, weightKg, now)) {
            repository.appendWeight(userId, weightKg, now);
        }
        log.info("[Profile] userId={} 记了一次体重 {}kg", userId, weightKg);
        return updated;
    }

    /**
     * 删掉记错的那一条体重流水，返回删除之后的画像。
     *
     * <p><b>删的是「记错的那一条」，不是「改历史」</b>：手滑打进 720 之后，
     * 那条线会永远钉在图上、还会把纵轴拉爆，而用户没有任何办法去掉它。
     * 所以页面上的记录列表每一行都带一个删除。
     *
     * <p><b>删不到不算错误</b>（同 {@code PgVectorIndexService#deleteBySource} 的口径）：
     * 这条不属于当前用户、或者已经被删过一次，结果都是「它不在了」，而调用方要做的事
     * 完全一样——重新读一遍列表。返 404 只会让前端多一条没有意义的错误分支。
     *
     * <p><b>删掉最新一条时，画像里的「当前体重」跟着回退到新的最新一条</b>：
     * 那个字段是「现在多少斤」，它的依据就是流水里最后一条。不回退的话，
     * 页面上会出现「当前 71.5kg」配一条到 70.8 就结束的曲线，看起来像图坏了。
     */
    public UserProfile deleteWeightRecord(String userId, Long id) {
        if (id == null) {
            return read(userId);
        }
        List<UserProfile.WeightRecord> before = repository.findWeightHistory(userId);
        boolean wasLatest = !before.isEmpty() && id.equals(before.get(0).id());
        if (repository.deleteWeightRecord(userId, id) == 0 || !wasLatest) {
            // 删的不是最新那条（或者压根没删到）：画像里的当前体重不受影响
            return read(userId);
        }

        List<UserProfile.WeightRecord> after = repository.findWeightHistory(userId);
        UserProfile current = read(userId);
        UserProfile rolledBack = new UserProfile(
                current.age(), current.heightCm(),
                after.isEmpty() ? null : after.get(0).weightKg(),
                current.goal(), current.activityLevel(), current.dietPreference(),
                current.avoidFood(), current.updatedAt());
        repository.save(userId, rolledBack);
        log.info("[Profile] userId={} 删掉了最新一条体重流水，当前体重回退为 {}kg",
                userId, rolledBack.weightKg());
        return rolledBack;
    }

    /**
     * 这一笔要不要真的写进流水。
     *
     * <p>规矩：<b>和最近一条不一样就写；一样的话，只有跨天才写。</b>
     * <ul>
     *   <li>不一样 → 当然要写，这就是趋势本身；</li>
     *   <li>一样且同一天 → 多半是把「记一笔」点了两下，或者用户在同一天先后称了两次。
     *       两条一模一样的记录画在图上就是两个重合的点，横轴还会被拉长一天，
     *       看起来像「这天轻了又重了」。所以不写；</li>
     *   <li>一样但不同天 → 要写。体重不变本身是信息（平台期），
     *       而且这条记录决定了曲线上有没有这个点。</li>
     * </ul>
     */
    private boolean shouldAppendWeight(String userId, double weightKg, LocalDateTime now) {
        Optional<UserProfile.WeightRecord> latest = repository.findLatestWeightRecord(userId);
        if (latest.isEmpty()) {
            return true;
        }
        UserProfile.WeightRecord last = latest.get();
        if (Double.compare(last.weightKg(), weightKg) != 0) {
            return true;
        }
        return last.recordedAt() == null || !last.recordedAt().toLocalDate().equals(now.toLocalDate());
    }

    /**
     * 落库，两条路径共同的收尾。
     *
     * <p>时间戳在这里统一盖，不由调用方传：它是「这次什么时候写进去的」，
     * 该由服务端说了算。让调用方传的话，工具那条路径一定会有人传 {@code now()}、
     * 另一条忘了传 null，库里就会出现一行没有更新时间的画像。
     *
     * @param previousWeight 改动<b>之前</b>的体重，用来判断这次要不要记流水
     */
    private UserProfile persist(String userId, UserProfile profile, Double previousWeight) {
        LocalDateTime now = LocalDateTime.now();
        UserProfile stamped = new UserProfile(
                profile.age(), profile.heightCm(), profile.weightKg(), profile.goal(),
                profile.activityLevel(), profile.dietPreference(), profile.avoidFood(), now);

        requireSaneWeight(stamped.weightKg());
        repository.save(userId, stamped);

        if (weightChanged(previousWeight, stamped.weightKg())) {
            repository.appendWeight(userId, stamped.weightKg(), now);
            log.info("[Profile] userId={} 体重 {} kg → {} kg，已记入体重历史",
                    userId, previousWeight, stamped.weightKg());
        }
        log.info("[Profile] userId={} 画像已写入", userId);
        return stamped;
    }

    /**
     * 体重必须落在一个像人的范围里，否则拒绝写入。
     *
     * <p><b>放在这里而不是放在新加的体重记录接口上</b>：画像有三条写入路径
     * （设置页表单、对话里的工具、体重记录接口），只在新那条路上校验，等于留了个
     * 「从设置页填 720 照样能存进去」的后门——而那种脏数据正是这张曲线图最怕的东西：
     * 一个 720 会把纵轴拉爆，其余的点挤成一条直线。
     *
     * <p>范围取得很宽（20–400 公斤）：它拦的是打错字和模型听错数，不是医学判断。
     * {@code null} 放行——那是「这一项没填」，不是「填了个 0」。
     *
     * <p>提示语里带上实际收到的值和上下限，用户才知道该改成什么，
     * 而不是只看到一句「体重不合法」。
     */
    private static void requireSaneWeight(Double weightKg) {
        if (weightKg != null && (weightKg < MIN_WEIGHT_KG || weightKg > MAX_WEIGHT_KG)) {
            throw ApiException.invalidProfile("error.profile.weightOutOfRange",
                    number(weightKg), number(MIN_WEIGHT_KG), number(MAX_WEIGHT_KG));
        }
    }

    /**
     * 这次真正被改掉的字段，没有改动就是空表。
     *
     * <p><b>为什么是「差异」而不是「模型这轮传了什么」。</b>模型经常把已知的值再传一遍
     * （工具描述专门叮嘱过它别这样，但它还是会），照传来报的话，用户每轮都会看到
     * 一条「已记住：体重 68kg」——而他这轮什么都没说。差异语义下这种重复写入是静默的，
     * 而那正是它该有的样子。
     *
     * <p>体重那一项特意复用 {@link #weightChanged}，和「要不要记流水」是同一个判据：
     * 两处各写一遍的话，早晚会出现「记了流水却没有提示」或者反过来，
     * 而这两种不一致都不会报错，只会让用户觉得数据对不上。
     *
     * <p>字符串字段不用再判空白：{@link UserProfile#merge} 已经把「传了空白」
     * 折成「保持原值」了，所以 merged 和 existing 在这种情况下本来就相等。
     */
    private static List<ProfileChange> diff(UserProfile before, UserProfile after) {
        List<ProfileChange> changes = new ArrayList<>();
        if (!Objects.equals(before.age(), after.age())) {
            changes.add(new ProfileChange(ProfileField.AGE, text(after.age())));
        }
        if (!Objects.equals(before.heightCm(), after.heightCm())) {
            changes.add(new ProfileChange(ProfileField.HEIGHT_CM, number(after.heightCm())));
        }
        if (weightChanged(before.weightKg(), after.weightKg())) {
            changes.add(new ProfileChange(ProfileField.WEIGHT_KG, number(after.weightKg())));
        }
        if (!Objects.equals(before.goal(), after.goal())) {
            changes.add(new ProfileChange(ProfileField.GOAL, after.goal()));
        }
        if (!Objects.equals(before.activityLevel(), after.activityLevel())) {
            // 传枚举名而不是 label：label 是没翻过的中文（ActivityLevel.parse 拿它当匹配依据），
            // 摆给英文用户看等于没翻。值该怎么显示由前端按当前语言决定
            changes.add(new ProfileChange(ProfileField.ACTIVITY_LEVEL,
                    after.activityLevel() == null ? null : after.activityLevel().name()));
        }
        if (!Objects.equals(before.dietPreference(), after.dietPreference())) {
            changes.add(new ProfileChange(ProfileField.DIET_PREFERENCE, after.dietPreference()));
        }
        if (!Objects.equals(before.avoidFood(), after.avoidFood())) {
            changes.add(new ProfileChange(ProfileField.AVOID_FOOD, after.avoidFood()));
        }
        return changes;
    }

    /** 整数原样，null 让调用方自己判。 */
    private static String text(Integer value) {
        return value == null ? null : value.toString();
    }

    /**
     * 数字转成给人看的样子：{@code 68.0 → "68"}，{@code 68.5 → "68.5"}。
     *
     * <p>{@code Double.toString} 会把整数体重写成 {@code 68.0}，提示里就会出现
     * 「体重 68.0kg」——那是从数据库列里直接漏出来的观感。
     * {@code stripTrailingZeros} 之后<b>必须</b>走 {@code toPlainString}：
     * 它会把 68 表示成 {@code 6.8E+1}，直接 toString 出去是科学计数法。
     */
    private static String number(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /**
     * 体重是不是真的变了。
     *
     * <p>判断的前提是「体重变了才记流水」：把已知的体重重复写一遍是常见操作
     * （模型尤其爱这么干，表单每次保存也会带上没改的体重），逐次照记的话，
     * 流水会被同一个数字淹没，趋势也就看不出来了。
     *
     * <p>{@code incoming} 为 null 是「现在没有体重」——那也算变化，
     * 但只在原来有值的时候才是（原来也没有就不必记一条空流水）。
     * 用 {@link Double#compare} 而不是 {@code !=}：这些值是装箱的 {@code Double}，
     * 拿 {@code !=} 比的是引用，同样的 70.0 在两个不同对象上会判成「变了」。
     */
    private static boolean weightChanged(Double previous, Double current) {
        if (current == null) {
            return false;
        }
        return previous == null || Double.compare(previous, current) != 0;
    }
}
