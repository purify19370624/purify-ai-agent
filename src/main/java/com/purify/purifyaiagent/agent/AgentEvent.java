package com.purify.purifyaiagent.agent;

import com.purify.purifyaiagent.model.ProfileChange;

import java.util.List;

/**
 * 智能体跑动过程中往外抛的事件，流式接口（SSE）就是把它一条条发出去。
 *
 * <p>为什么不是「直接把最终文本流出去」：这个智能体的中间过程本身就是它最有价值的部分——
 * 调了哪个工具、工具返回了什么、是不是被看门狗拦下过。只流最终答案的话，
 * 用户看到的和普通对话没有区别，循环检测这套机制等于白做了。
 *
 * <p>阻塞式接口不用这个类型，它直接返回 {@link AgentResult}：那边没有「中间过程」要展示，
 * 事件流攒完就是那个结果。
 *
 * @param type    事件类型，SSE 里同时用作事件名（{@code event: TOOL_CALL}）
 * @param text    事件正文，含义随类型而定，见下面各工厂方法
 * @param state   只有终态事件（{@link Type#FINAL} / {@link Type#QUESTION} / {@link Type#ERROR}）
 *                才非 null，告诉调用方这次 run 是以什么状态收的尾；中间事件一律为 null。
 *                <b>加新事件时不要碰它</b>——{@code PurifyManus#chatStream} 靠
 *                {@code state() != null} 判终态，非收尾事件带上它会把落库的问答记错
 * @param changes 只有 {@link Type#PROFILE_UPDATED} 会用到，装着这次改动的画像字段；
 *                其余事件一律是空表。之所以给结构化数据单开一个字段而不是塞进 {@code text}：
 *                前端一旦漏了这个事件的 case，兜底分支会把 {@code text} 当成正文片段
 *                追加进回答里——塞 JSON 进去的话，用户会看到答案末尾挂着一串花括号
 */
public record AgentEvent(Type type, String text, AgentState state, List<ProfileChange> changes) {

    /**
     * 把 null 折成空表。这样 {@code changes} 永远非 null，前端不用到处判空，
     * 序列化出来也永远有这一项——形状固定比忽有忽无好接。
     */
    public AgentEvent {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    /**
     * 不带结构化数据的事件。绝大多数事件（TEXT / STEP / FINAL……）都属于这一类，
     * 给它们省掉一个永远是空表的参数。
     */
    public AgentEvent(Type type, String text, AgentState state) {
        this(type, text, state, List.of());
    }

    public enum Type {

        /** 新的一步开始，text 是步序号。 */
        STEP,

        /** 模型输出的文本片段。流式下是逐段到达的，阻塞式下整段一次到达。 */
        TEXT,

        /** 模型要求调用某个工具，text 形如 {@code maps_weather({"city":"杭州"})}。 */
        TOOL_CALL,

        /** 某个工具返回了，text 是工具的返回内容（可能被截断）。 */
        TOOL_RESULT,

        /** 看门狗发声：命中了循环判据或者步数超预算，text 是处置说明。 */
        LOOP_SIGNAL,

        /**
         * 开场预检索的结果：跳过了、命中几条、还是失败。text 是一句摘要。
         *
         * <p>为什么要有这个类型：检索是「悄悄发生」的——命中也好、没查也好，
         * 用户的观感都是「模型直接开始回答了」。答得不对或者答得很慢的时候，
         * 分不清是知识库没召回到、压根没查、还是查了没被采纳。这条事件把前两种可能直接摆出来。
         *
         * <p><b>跳过也要发</b>，那正是「看不到检索在工作」的最坏情况。
         * 正文里只放条数和文档名，不放切片原文——SSE 是逐条推送的，塞原文会把流刷爆。
         */
        RETRIEVAL,

        /**
         * 模型往用户画像里写了东西，{@code changes} 里装了改了哪些字段。
         * 前端在气泡下面显示一行「已记住」，让用户知道模型刚才把什么记下来了。
         *
         * <p><b>为什么不复用 TOOL_CALL / TOOL_RESULT。</b>轻语这条链路根本不发那两种事件
         * （工具调用被 DashScope 的流式实现内部消化了，见 {@code SlimApp#chatStream} 的说明）；
         * 就算在智能体那条链路上，过程区也是「给想深挖的人看的调试视图」，默认折叠，
         * 而这个提示是给用户看的回执。两者的受众和呈现都不一样，
         * 合成一个只会让前端到处判「这条到底要不要显眼地画」。
         *
         * <p><b>{@code text} 刻意留空串、{@code state} 为 null。</b>留空是兜底：
         * 前端万一漏了这个 case 落到「未知事件当正文追加」那条兜底上，追加的是空串，
         * 而不是一句中文或一段 JSON。state 为 null 则保证它不会被当成收尾事件、
         * 也不会被算成一步。
         */
        PROFILE_UPDATED,

        /** 需要用户回答，text 是问题。这次 run 就此暂停。 */
        QUESTION,

        /** 正常跑完，text 是最终答复。 */
        FINAL,

        /** 出错退出，text 是给用户看的失败说明。 */
        ERROR
    }

    /**
     * 每一步开头的那条「第 N 步」。
     *
     * <p>收 {@link AgentRun} 而不是一个 int，是为了拿到这次 run 的语言：
     * 这一段会原样显示在界面的过程区里，英文用户不该看到「第 3 步」。
     * 序号仍然从 run 上取（{@code nextStepIndex}），保证它和记录里的口径是同一个。
     */
    public static AgentEvent step(AgentRun run) {
        return new AgentEvent(Type.STEP, run.i18n().get("agent.step", run.nextStepIndex()), null);
    }

    public static AgentEvent text(String delta) {
        return new AgentEvent(Type.TEXT, delta, null);
    }

    public static AgentEvent toolCall(String name, String arguments) {
        return new AgentEvent(Type.TOOL_CALL, name + "(" + arguments + ")", null);
    }

    public static AgentEvent toolResult(String name, String result) {
        return new AgentEvent(Type.TOOL_RESULT, name + " → " + result, null);
    }

    public static AgentEvent loopSignal(String message) {
        return new AgentEvent(Type.LOOP_SIGNAL, message, null);
    }

    /**
     * 开场预检索的结果。
     *
     * <p>state 为 null，因此 {@code PurifyManus#chatStream} 的数步逻辑不会把它算成一步，
     * {@code chat_record.steps} 的口径不受影响。
     */
    public static AgentEvent retrieval(String summary) {
        return new AgentEvent(Type.RETRIEVAL, summary, null);
    }

    /**
     * 这一轮写进画像的字段。
     *
     * <p><b>不在这里拼「体重 68kg」。</b>拼出来的是句子，会冻在事件里，
     * 用户切语言不会重算——和前端那条「存键不存句子」是同一条规矩。
     * 这里只给字段名和值，说法由前端按当前语言取。
     */
    public static AgentEvent profileUpdated(List<ProfileChange> changes) {
        return new AgentEvent(Type.PROFILE_UPDATED, "", null, changes);
    }

    public static AgentEvent question(String question) {
        return new AgentEvent(Type.QUESTION, question, AgentState.WAITING_FOR_USER);
    }

    public static AgentEvent finished(String output) {
        return new AgentEvent(Type.FINAL, output, AgentState.FINISHED);
    }

    public static AgentEvent aborted(String reason) {
        return new AgentEvent(Type.FINAL, reason, AgentState.ABORTED);
    }

    /**
     * 命中敏感词，被按策略拦下。text 是那段引导话术。
     *
     * <p>走的是 {@link Type#FINAL} 而不是 {@link Type#ERROR}：话术本身就是要展示给用户的内容，
     * 标成 ERROR 会让界面画一个红色「生成失败」，而用户看到的是「服务坏了」——
     * 和「顾问主动拒绝回答」完全不是一回事。状态单开一个 {@link AgentState#BLOCKED}，
     * 界面仍然能把它和正常答完区分开。
     */
    public static AgentEvent blocked(String reply) {
        return new AgentEvent(Type.FINAL, reply, AgentState.BLOCKED);
    }

    public static AgentEvent error(String message) {
        return new AgentEvent(Type.ERROR, message, AgentState.ERROR);
    }

    /** 按 run 的终态挑一个终态事件。循环收尾时只看状态，不关心它是怎么走到这一步的。 */
    public static AgentEvent terminal(AgentState state, String output, String question) {
        return switch (state) {
            case WAITING_FOR_USER -> question(question);
            case FINISHED -> finished(output);
            case ABORTED -> aborted(output);
            case BLOCKED -> blocked(output);
            case ERROR -> error(output);
            // RUNNING 不是终态，走到这里说明调用方在循环中途问「结束了没」，那是它的 bug
            case RUNNING -> throw new IllegalStateException("run 还在跑，没有终态事件");
        };
    }
}
