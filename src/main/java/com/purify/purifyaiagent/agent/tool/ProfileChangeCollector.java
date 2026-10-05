package com.purify.purifyaiagent.agent.tool;

import com.purify.purifyaiagent.model.ProfileChange;
import com.purify.purifyaiagent.model.ProfileField;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 一次对话里模型对画像做的改动，攒着等 SSE 收尾时一次性发出去。
 *
 * <p><b>为什么必须是线程安全的。</b>写它的线程和读它的线程不是同一个：工具是在
 * {@code DashScopeChatModel.internalStream} 的 {@code flatMap} 里被执行的，那时候流已经跑在
 * Reactor/Netty 的线程上；而 drain 发生在流结束的那一刻。更要紧的是并发本身就可达——
 * 一次响应里模型可能返回多个 tool call，{@code flatMap} 也允许多个内层流同时在飞，
 * 两处 {@code executeToolCalls} 会并行。
 *
 * <p>不用 {@code ArrayList} + {@code synchronized}：这里只有「追加」和「一次取空」两种动作，
 * {@link ConcurrentLinkedQueue} 一个类就够了，而且每个 add/poll 自带内存屏障，
 * 读线程一定看得到写线程追加的内容。
 *
 * <p><b>绝不能把它做成 {@code SlimApp} 或 {@code UserProfileTool} 的字段。</b>
 * 那两个都是单例 Bean，字段会被所有会话共享——症状是「A 说的话出现在 B 的提示里」，
 * 而且只在并发时复现。所以它是每请求一个，跟着 {@link ToolContexts} 走。
 */
public final class ProfileChangeCollector {

    private final Queue<ProfileChange> pending = new ConcurrentLinkedQueue<>();

    /** 工具写完画像后调它。空表直接返回，省掉一次无意义的入队。 */
    public void add(List<ProfileChange> changes) {
        if (changes != null && !changes.isEmpty()) {
            pending.addAll(changes);
        }
    }

    /**
     * 取走这一轮攒下的全部改动，之后这个收集器就是空的了。
     *
     * <p><b>同一字段只留最后一次。</b>模型一轮里调两次 {@code updateUserProfile}
     * （先说 68 又说 66）是可能的，两条都显示出来只会让人怀疑哪条才算数。
     * {@link LinkedHashMap} 的 put 不改变已有键的位置，所以顺序是「这个字段第一次被改到的顺序」，
     * 值是最新那个——前端那一行的顺序因此和模型说话的顺序一致。
     *
     * <p>只该被调一次（收尾那一次）。再调返回空表，这正是「取走」该有的语义。
     */
    public List<ProfileChange> drain() {
        Map<ProfileField, ProfileChange> last = new LinkedHashMap<>();
        for (ProfileChange change; (change = pending.poll()) != null; ) {
            last.put(change.field(), change);
        }
        return List.copyOf(last.values());
    }
}
