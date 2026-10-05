package com.purify.purifyaiagent.model;

/**
 * 一次画像改动：改了哪个字段、改成了什么。
 *
 * <p><b>装的是数据，不是句子。</b>字段名是给前端查文案用的键，值是原样
 * （数字是去掉尾零的字符串、活动水平是枚举名、自由文本是用户自己的话）。
 * 后端<b>不拼</b>「体重 68kg」这样的中文——那句话一旦拼出来就冻在事件里了，
 * 用户切到英文不会重算。这和前端 i18n 那条「存键不存句子」是同一条规矩。
 *
 * <p>不带旧值：卡片上显示的是「已记住什么」，不是「从什么改成了什么」。
 * 将来若要做「68 → 66」，加一个 {@code previous} 组件即可，现在不加——
 * 加了没有读取方的字段，和 {@code AgentRun} 里删掉的那个 loopHits 集合是同一类垃圾。
 *
 * @param field 改的是哪个字段，序列化出去是枚举名
 * @param value 改成了什么，已转成给人看的样子（见 {@code ProfileService#number}）
 */
public record ProfileChange(ProfileField field, String value) {
}
