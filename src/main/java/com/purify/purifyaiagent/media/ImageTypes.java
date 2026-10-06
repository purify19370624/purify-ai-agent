package com.purify.purifyaiagent.media;

import com.purify.purifyaiagent.exception.ApiException;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 上传图片的类型判定。
 *
 * <p>从 {@code SlimAppController} 里抽出来的：那里原本有一个私有的 {@code resolveImageType}，
 * 而头像上传要挡的东西一模一样（是不是图片、是不是能落盘的格式）。
 * 复制一份的代价不是多几行代码，是<b>两处会慢慢走偏</b>——
 * 哪天给图片解读放开 webp，头像那边多半不会跟着改，而这种不一致不会有任何报错，
 * 只是某个入口突然「传不上去」。
 *
 * <p>判定优先用上传时带的 {@code Content-Type}，拿不到才回退到按文件名推断：
 * 后缀是用户可以随便改的，Content-Type 由浏览器按文件内容填，更可信。
 */
public final class ImageTypes {

    /**
     * 允许落盘的图片格式 → 扩展名。
     *
     * <p><b>是个白名单而不是「image/ 开头就放行」</b>，因为这里的扩展名会被拼进
     * 磁盘上的文件名。取一个我们不认识的子类型（比如 {@code image/heic}、
     * 或者更糟的 {@code image/svg+xml}）就只能瞎编一个后缀，
     * 而 svg 落到静态目录里是可以带脚本执行的。认不出来就拒绝，比猜一个安全。
     */
    private static final Map<String, String> ALLOWED = Map.of(
            "png", "png",
            "jpeg", "jpg",
            "webp", "webp",
            "gif", "gif");

    /** 默认上限 2MB。头像这种用途足够了，而全局 multipart 的 10MB 是给手机原图定的。 */
    public static final long DEFAULT_MAX_BYTES = 2L * 1024 * 1024;

    private ImageTypes() {
    }

    /**
     * 判定 MIME 类型，顺便挡掉非图片。
     *
     * <p>挡在入口的好处是：明显不是图片的请求不会走到模型那一层，
     * 既不浪费 token，也不用让模型去猜一堆乱码字节。
     */
    public static MimeType resolve(MultipartFile file) {
        String contentType = file.getContentType();
        MimeType mimeType;
        try {
            mimeType = (contentType == null || contentType.isBlank())
                    ? MediaTypeFactory.getMediaType(file.getResource()).orElse(null)
                    : MimeTypeUtils.parseMimeType(contentType);
        } catch (IllegalArgumentException exception) {
            // MimeTypeUtils 对畸形 Content-Type 抛的是 InvalidMimeTypeException
            throw ApiException.invalidImage("error.image.contentTypeUnparsable", contentType);
        }

        if (mimeType == null) {
            throw ApiException.invalidImage("error.image.unknownFormat");
        }
        if (!"image".equalsIgnoreCase(mimeType.getType())) {
            throw ApiException.invalidImage("error.image.notImage", mimeType);
        }
        return mimeType;
    }

    /**
     * 一次判定拿到的两样东西：可以安全拼进文件名的扩展名，和回给浏览器的 Content-Type。
     *
     * <p>合成一个返回值是因为它们本来就出自同一次判定。拆成两个方法的话，调用方要么
     * 判定两遍，要么自己再维护一张「扩展名 → MIME」的映射表——而后者正是这个类
     * 当初要消掉的那种重复（见类注释）。
     */
    public record StorableImage(String extension, MimeType contentType) {
    }

    /**
     * 判定类型，返回落盘/入库需要的扩展名与 Content-Type。
     *
     * <p>扩展名字符串来自上面那张常量表，不含用户输入的任何字符，
     * 所以调用方拿它拼对象键或文件名不存在路径穿越或注入的问题。
     *
     * <p>Content-Type 要一起带出去，是因为它得**原样写进对象存储**：
     * 不写的话 OSS 会按 {@code application/octet-stream} 存，浏览器拿到它多半
     * 不会当成图片渲染，表现是点头像变成下载一个文件。
     */
    public static StorableImage resolveStorable(MultipartFile file) {
        MimeType mimeType = resolve(file);
        String subtype = mimeType.getSubtype() == null ? "" : mimeType.getSubtype().toLowerCase();
        String extension = ALLOWED.get(subtype);
        if (extension == null) {
            throw ApiException.invalidImage("error.image.formatUnsupported", String.join(" / ", ALLOWED.values()));
        }
        return new StorableImage(extension, mimeType);
    }

    /** 校验大小。{@code maxBytes} 传 0 或负数表示不检查。 */
    public static void requireWithinSize(MultipartFile file, long maxBytes, String what) {
        if (maxBytes > 0 && file.getSize() > maxBytes) {
            throw ApiException.invalidImage("error.image.tooLarge", what, maxBytes / 1024 / 1024, file.getSize() / 1024);
        }
    }
}
