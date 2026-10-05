package com.purify.purifyaiagent.controller;

import com.purify.purifyaiagent.auth.CurrentUser;
import com.purify.purifyaiagent.auth.LoginUser;
import com.purify.purifyaiagent.auth.RequireLogin;
import com.purify.purifyaiagent.model.UserProfile;
import com.purify.purifyaiagent.model.UserProfileUpdateRequest;
import com.purify.purifyaiagent.model.UserProfileView;
import com.purify.purifyaiagent.model.WeightRecordRequest;
import com.purify.purifyaiagent.profile.ProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户画像与体重记录的自助读写。
 *
 * <pre>
 *   GET    /api/profile                读当前用户的画像（含完整体重流水）
 *   PUT    /api/profile                改画像（七个字段一起，整体替换）
 *   POST   /api/profile/weight         记一次体重
 *   DELETE /api/profile/weight/{id}    删掉记错的那一条流水
 * </pre>
 *
 * <p><b>在这之前，画像是只能通过对话改的。</b>唯一的写入口是
 * {@code UserProfileTool.updateUserProfile}——也就是说用户想改身高，
 * 得去聊天里跟模型说一句「我 175」。设置页要能直接填表，
 * 就有了 {@code PUT} 这条路径。
 *
 * <p><b>体重为什么又多了两条自己的路径</b>：{@code PUT} 是<b>整体替换</b>，
 * 语义是「表单上是全部内容」。而「今天称了一下，71.5」只说了体重这一件事，
 * 走 {@code PUT} 的话前端得先把七个字段读出来再原样发回去——中间任何一次读失败、
 * 或者用户同时在另一台设备上改了忌口，都会被这次覆盖写掉。
 * 所以体重的「记一笔」和「删一条」各有一个只动体重的小接口。
 *
 * <p>三条路径（表单 / 对话里的工具 / 这两个接口）共用 {@link ProfileService}，
 * 所以「先读再合并」「什么情况才记流水」「体重范围」这些规则不会走偏。
 * 这里**不做任何额外的合并或校验**，转手就交给 service——
 * 在这层再判断一次，就等于把规则抄了第二遍。
 */
@Slf4j
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * 读画像。
     *
     * <p>没有画像时返回一份空画像而不是 404：对设置页来说「还没填过」和「填了但都是空的」
     * 是同一种状态——表单显示空白。返 404 的话前端每打开一次抽屉都要先处理一次错误，
     * 而那不是错误。
     *
     * <p><b>体重流水给的是完整的一份，不是喂模型那 10 条。</b>它同时被设置页
     * 和体重变化页当数据源，而那个页面要画的是几个月的趋势（见
     * {@code ProfileService#history} 与 {@code #recentHistory} 的分工）。
     */
    @GetMapping
    @RequireLogin
    public UserProfileView read(@CurrentUser LoginUser me) {
        UserProfile profile = profileService.read(me.id());
        return UserProfileView.from(profile, profileService.history(me.id()));
    }

    /**
     * 改画像。**整体替换**：表单上是什么，库里就是什么。
     *
     * <p>用替换而不是合并，是为了让用户能<b>清掉</b>一个字段。合并语义把空值当成
     * 「这次没提」，于是忌口填错了想删掉、删完保存回来看还在——那种 bug 很难解释。
     * 代价是前端每次必须把七个字段都发上来，只发改动过的会把其余的当成清空。
     *
     * <p>唯一的例外是<b>七个字段全是 null</b>：那是「请求里什么都没有」，
     * 更像前端拼错了请求体，而不是「用户想把画像清空」。这种情况按无变化处理、
     * 原样返回当前画像——总比一个字都没动却把用户填过的资料全抹掉强。
     *
     * <p>返回改完之后<b>完整</b>的画像，前端可以直接拿它刷新表单，不用再 GET 一次。
     */
    @PutMapping
    @RequireLogin
    public UserProfileView update(@RequestBody UserProfileUpdateRequest request,
                                  @CurrentUser LoginUser me) {
        UserProfile incoming = request.toProfile();
        if (incoming.isBlank()) {
            log.debug("[Profile] userId={} 请求里一个字段都没有，按无变化处理", me.id());
            return UserProfileView.from(profileService.read(me.id()), profileService.history(me.id()));
        }

        UserProfile saved = profileService.replace(me.id(), incoming);
        log.info("[Profile] userId={} 通过设置页更新了画像", me.id());
        return UserProfileView.from(saved, profileService.history(me.id()));
    }

    /**
     * 记一次体重。
     *
     * <p>只动体重这一项，其余字段（身高、目标、忌口……）原样保留——所以这个接口
     * 不需要请求体里带上它们，也不会因为一次记录把别的字段冲掉。
     *
     * <p>返回的是<b>整份画像视图</b>（含刷新后的流水），前端拿它可以一次把页面
     * 全刷新掉，不用再 GET 一次。这是这个项目里写接口的一贯做法：
     * {@code PUT /api/profile} 也是这么返回的。
     */
    @PostMapping("/weight")
    @RequireLogin
    public UserProfileView recordWeight(@RequestBody WeightRecordRequest request,
                                        @CurrentUser LoginUser me) {
        UserProfile saved = profileService.recordWeight(me.id(),
                request == null ? null : request.weightKg());
        return UserProfileView.from(saved, profileService.history(me.id()));
    }

    /**
     * 删掉记错的那一条体重流水。
     *
     * <p>{@code user_id} 由登录态给，不从路径里来：{@code id} 是全局自增的，
     * 只按 id 删就等于开了一个「删别人记录」的口子。
     *
     * <p><b>删不到也返回 200</b>（同 {@code KnowledgeBaseController#delete} 的口径）：
     * 「这条不存在」和「这条不属于你」对调用方是同一件事——刷新一下列表，
     * 该在的还在、不该在的没了。返 404 只会让前端多一条没有意义的错误分支。
     */
    @DeleteMapping("/weight/{id}")
    @RequireLogin
    public UserProfileView deleteWeight(@PathVariable Long id, @CurrentUser LoginUser me) {
        UserProfile saved = profileService.deleteWeightRecord(me.id(), id);
        return UserProfileView.from(saved, profileService.history(me.id()));
    }
}
