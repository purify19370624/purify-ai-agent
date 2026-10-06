package com.purify.purifyaiagent.auth;

import com.aliyun.oss.model.ObjectMetadata;
import com.purify.purifyaiagent.config.AliyunOssClient;
import com.purify.purifyaiagent.exception.ApiException;
import com.purify.purifyaiagent.media.ImageTypes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * 把用户头像传到阿里云 OSS，返回可直接放进 {@code <img src>} 的地址。
 *
 * <h2>为什么从「存本地」改成了「传 OSS」</h2>
 *
 * 原先它写到 {@code <user.dir>/tmp/avatar}，再由 {@code StaticResourceConfig} 把
 * {@code /files/avatar/**} 映射到那个目录。那个方案本身没错（头像小、不需要被用户带走），
 * 但有三个它绕不过去的代价，而这套服务现在要拆开部署、还要能水平扩容：
 *
 * <ol>
 *   <li><b>目录叫 tmp，头像却不是临时数据。</b>它落在整个被 gitignore 的 tmp 树里，
 *       容器一重建就没了，而数据库里还留着一条条指向不存在文件的地址，页面上一片裂图；</li>
 *   <li><b>多实例会互相看不见。</b>文件在各自容器的本地磁盘上，用户下一次请求被打到
 *       另一个实例就是 404。数据库里存的是路径，文件却在另一台机器上；</li>
 *   <li><b>换机器要连带搬文件。</b>备份数据库不等于备份了头像，恢复出来的站点头像全裂。</li>
 * </ol>
 *
 * <p>存进 OSS 之后这三点同时消失：地址是绝对地址、对象在所有实例之间共享、
 * 备份跟着 bucket 走。代价是头像变成一次外网调用，以及多一份凭证依赖
 * （OSS 那套被占位值坑过一次，见 application-local.yml 里的注释）。
 *
 * <p><b>不做数据迁移。</b>库里那些形如 {@code /files/avatar/xxx.jpg} 的旧值是**相对地址**，
 * 和现在生成的绝对地址形状不同，{@link #deleteQuietly} 会直接跳过它们（见那个方法的注释）。
 * 也就是说老头像会原样留在本地磁盘上、由 {@code StaticResourceConfig} 那条旧映射继续提供，
 * 直到对应用户自己换一次头像为止，之后那个文件才会变成没人引用的孤儿。
 *
 * <p><b>返回绝对地址，不再返回相对地址。</b>原来的相对地址有个好处是换域名不用改，
 * 而 OSS 的地址天然就是这个形状——它本来就在另一个域名上，没有「跟着当前 origin 走」
 * 这回事。所以这个取舍到这里自然消失了。
 */
@Slf4j
public class AvatarStorage {

    /**
     * 对象键前缀。
     *
     * <p>它同时是两个东西：对象在 bucket 里的目录，以及<b>「这个地址是不是我们生成的」的判据</b>。
     * 后者是删旧头像时的安全边界，见 {@link #objectKeyOf}。
     */
    private static final String KEY_PREFIX = "avatar/";

    private final AliyunOssClient oss;

    public AvatarStorage(AliyunOssClient oss) {
        this.oss = oss;
    }

    /**
     * 存一份头像，返回形如
     * {@code https://<bucket>.<endpoint>/avatar/<uuid>.png} 的公开地址。
     *
     * <p>对象名**完全由服务端生成**（UUID + 从白名单里取的扩展名），
     * 用户给的原始文件名一个字符都不参与——这样就不用去防路径穿越，
     * 也不用担心「同一个用户传两次同名文件互相覆盖」。
     * 用 UUID 而不是 userId 命名，还顺带避免了「换了头像但浏览器拿的是缓存」：
     * 地址变了，缓存自然失效。
     */
    public String store(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw ApiException.invalidImage("error.auth.avatarRequired");
        }
        // 没配对象存储时给一句人话，而不是让它掉进下面的 NPE / 认证失败里。
        // 这里**不**学 ToolConfig 那种「安静地少一个工具」：头像是一个用户点得到的按钮，
        // 没有中间态可用，只能明确告诉他传不了
        if (!oss.isConfigured()) {
            log.warn("[Avatar] 收到头像上传，但 aliyun.oss.* 没有配齐");
            throw ApiException.invalidImage("error.auth.avatarStorageUnavailable");
        }
        ImageTypes.requireWithinSize(file, maxBytes, "头像");
        // 先判定类型再上传：明显不是图片的请求不该在 bucket 里留下任何东西
        ImageTypes.StorableImage image = ImageTypes.resolveStorable(file);

        String objectKey = KEY_PREFIX + UUID.randomUUID() + "." + image.extension();

        ObjectMetadata metadata = new ObjectMetadata();
        // Content-Type 必须显式给：不写的话 OSS 按 application/octet-stream 存，
        // 浏览器拿到 octet-stream 多半不当图片渲染，表现是点头像变成下载一个文件
        metadata.setContentType(image.contentType().toString());
        metadata.setContentLength(file.getSize());

        try (InputStream input = file.getInputStream()) {
            oss.get().putObject(oss.bucket(), objectKey, input, metadata);
        } catch (IOException | RuntimeException exception) {
            // 把原因带出来：上传失败几乎都是凭证、Bucket 名或读写权限的问题，
            // 只说一句「上传失败」的话，用户和开发者都无从下手
            log.error("[Avatar] 上传头像到 OSS 失败：{}", objectKey, exception);
            throw ApiException.invalidImage("error.auth.avatarSaveFailed", exception.getMessage());
        }

        String url = oss.publicUrl(objectKey);
        log.info("[Avatar] 已保存头像：{}（{} 字节）", url, file.getSize());
        return url;
    }

    /**
     * 删掉上一张头像。
     *
     * <p><b>失败只记日志不上抛。</b>用户换头像这个动作已经成功了，
     * 为了一个删不掉的旧对象把整个请求变成 500，是本末倒置
     * （同 {@code ChatRecordRepository#save} 的取向）。
     */
    public void deleteQuietly(String url) {
        String objectKey = objectKeyOf(url);
        if (objectKey == null) {
            return;
        }
        try {
            oss.get().deleteObject(oss.bucket(), objectKey);
            log.info("[Avatar] 已删除旧头像：{}", objectKey);
        } catch (RuntimeException exception) {
            log.warn("[Avatar] 删除旧头像失败（不影响本次更换）：{} 原因={}", url, exception.getMessage());
        }
    }

    /**
     * 从头像地址里取出对象键，取不到返回 {@code null}。
     *
     * <p><b>必须严格。</b>这个字段来自数据库，而数据库里的值理论上是我们自己写的，
     * 但「理论上」不是安全边界：一旦有人手工改过库、或者哪天写入路径多了个口子，
     * 这里就成了一个<b>删掉 bucket 里任意对象</b>的原语。
     *
     * <p>对象存储上这一条比原先删本地文件严重得多：本地那个目录里只有头像，
     * 而 bucket 里还放着别的东西（生成的 PDF 就在 {@code pdf/} 下面），
     * 一次误删是找不回来的。所以判据不是「以 bucket 开头」，
     * 而是**必须正好以本服务生成头像的那个前缀开头，且其余部分是一个纯文件名**。
     *
     * <p>库里的旧值（{@code /files/avatar/xxx.jpg}，相对地址）会在这里被跳过：
     * 它不以 OSS 前缀开头。这正是「不做数据迁移」的落地方式——
     * 老头像继续留在本地磁盘上由旧映射提供，不会被这次改动误删。
     */
    private String objectKeyOf(String url) {
        String prefix = oss.publicUrl(KEY_PREFIX);
        if (url == null || !url.startsWith(prefix)) {
            log.debug("[Avatar] 头像地址不是本服务生成的 OSS 地址，跳过删除：{}", url);
            return null;
        }
        String name = url.substring(prefix.length());
        if (name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            log.warn("[Avatar] 头像对象名不合法，跳过删除：{}", url);
            return null;
        }
        return KEY_PREFIX + name;
    }
}
