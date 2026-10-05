package com.purify.purifyaiagent.model;

/**
 * 画像里可以被改写的字段。
 *
 * <p>只列「模型能通过 {@code updateUserProfile} 改的」那七个。{@code updatedAt} 不在里面：
 * 它是写入时盖的时间戳，不是用户告诉我们的信息。
 *
 * <p><b>枚举名同时是线上契约。</b>它会被序列化进 SSE 事件（{@code "field":"WEIGHT_KG"}），
 * 前端拿这个名字去查自己的文案表，所以改名等于改契约——而改错的症状是
 * 「那行提示少了一项」，不报任何错、也没有日志。
 *
 * <p>不加 {@code @JsonValue} 换成 camelCase：{@code model} 包里一个 Jackson 注解都没有，
 * 而大写枚举名在 JSON 里已经是本项目的惯例（{@code "state":"FINISHED"}、
 * {@code "activityLevel":"SEDENTARY"}），{@code "field":"WEIGHT_KG"} 读起来是一致的。
 *
 * <p>加字段时记得同时改前端 {@code ChatRoom.vue} 的 {@code REMEMBER_ITEM_KEYS}
 * 和两个语言包，否则新字段会被静默丢掉。
 */
public enum ProfileField {

    AGE,
    HEIGHT_CM,
    WEIGHT_KG,
    GOAL,
    ACTIVITY_LEVEL,
    DIET_PREFERENCE,
    AVOID_FOOD
}
