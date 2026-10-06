package com.purify.purifyaiagent.model;

/**
 * 登录请求。
 *
 * <p><b>{@code username} 这一栏用户名和邮箱都能填。</b>哪个优先、以及为什么不是
 * 「含 @ 就当邮箱」，写在 {@code UserRepository#findForLogin} 上。
 *
 * <p>字段名保持 {@code username}，没有改成 {@code account} 之类：它是一个已经对外的
 * JSON 字段，改名要一起动前端、以及任何存下来的接口调试配置（Apifox 之类），
 * 换来的只是措辞更准一点——而「这里能填什么」由登录页的标签和后端那句
 * {@code error.auth.badCredentials} 表达更直接。
 *
 * <p>为什么不是「只用邮箱」：项目里那个超级用户叫 {@code root_agent}，而它没有邮箱
 * （{@code user.email} 可空）。只用邮箱当账号的话，种子账号就得凭空编一个邮箱出来，
 * 而编出来的地址是不通邮的——一旦它需要找回密码就会卡死。
 * 所以用户名必须能单独用；邮箱是在此之上**追加**的一种登录方式，
 * 这一条在 {@code AuthController} 的种子用户那部分也有对应的说明。
 *
 * @param username 登录名：用户名或邮箱
 * @param password 明文密码，只用于这一次比对
 */
public record LoginRequest(String username, String password) {
}
