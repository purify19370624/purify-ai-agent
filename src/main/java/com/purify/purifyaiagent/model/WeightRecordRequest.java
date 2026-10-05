package com.purify.purifyaiagent.model;

/**
 * 「记一次体重」的请求体。
 *
 * <p>只有一个字段，而且刻意不做成 {@code @RequestParam}：体重是要写进库的数值，
 * 挂在 URL 上会进 access log（一行「71.5」混在路径里，谁也说不清它是什么），
 * 而且请求体这条路和 {@code PUT /api/profile} 一致，前端少记一种写法。
 *
 * <p>用 {@code Double} 而不是 {@code double}：字段缺失时 Jackson 会留成 {@code null}，
 * 由 {@code ProfileService} 判成「没给体重」并报一句能看懂的话；
 * 写成基本类型的话，缺字段会静默变成 0.0，然后被范围校验拦下——
 * 报出来的是「体重 0 不合理」，而真正的问题是「请求里没有这个字段」。
 *
 * <p>范围校验<b>不在这里</b>：它属于业务规则，三条写入路径（设置页表单、
 * 对话里的工具、这个接口）必须共用同一条，见 {@code ProfileService#requireSaneWeight}。
 */
public record WeightRecordRequest(Double weightKg) {
}
