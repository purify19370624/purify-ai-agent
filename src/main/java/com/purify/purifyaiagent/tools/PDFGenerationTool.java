package com.purify.purifyaiagent.tools;

import com.aliyun.oss.model.ObjectMetadata;
import com.purify.purifyaiagent.config.AliyunOssClient;
import com.purify.purifyaiagent.resource.ResourceKind;
import com.purify.purifyaiagent.resource.ResourceRecorder;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

/**
 * 把一段文字渲染成中文 PDF 并上传到阿里云 OSS，返回下载链接。
 * 体检报告、一周食谱这类「拿得走」的产出用它。
 *
 * <p><b>OSS 客户端是注入的，而且是全应用共用的那一个</b>（见 {@link AliyunOssClient}）。
 * 原先这里自己懒创建一个，头像那边另起一套；两套并存的问题不是多几条连接，
 * 而是「配置齐没齐」和「endpoint 怎么剥协议头」会出现**两份判定**，
 * 而那种不一致只会在其中一个功能上暴露。
 *
 * <p>懒创建的性质没有丢，只是挪进了那个共用的类：构造期不碰网络，
 * 所以没配 OSS 的应用照样能启动；一次都没用过 OSS 的场景也不必养着一个连接池。
 *
 * <p><b>本类不再负责关闭客户端。</b>那个客户端是共用的，谁关谁就会把对方
 * （头像上传）的连接一起掐掉；关闭统一由 {@link AliyunOssClient#close()} 在容器退出时做。
 */
@Slf4j
public class PDFGenerationTool {

    private final AliyunOssClient oss;

    /** 产出之后往资料库记一笔。见 {@code ResourceRecorder}——它不抛异常，不会拖累本工具。 */
    private final ResourceRecorder resourceRecorder;

    public PDFGenerationTool(AliyunOssClient oss, ResourceRecorder resourceRecorder) {
        this.oss = oss;
        this.resourceRecorder = resourceRecorder;
    }

    @Tool(description = "把内容生成一份 PDF 文件并返回下载链接。"
            + "适合把食谱、计划、报告这类内容整理成用户能保存下来带走的东西。"
            + "生成好之后，务必把返回的下载链接原样放进你的最终回答里，用户要靠它去下载。")
    public String pdfGenerate(
            @ToolParam(description = "保存的文件名，例如 一周食谱.pdf，不要带路径") String fileName,
            @ToolParam(description = "要写进 PDF 的完整内容，支持换行") String content,
            ToolContext toolContext) {

        if (!oss.isConfigured()) {
            log.warn("[PDFGenerationTool] 被调用，但 aliyun.oss.* 没有配齐，无法生成 PDF");
            return "生成 PDF 失败：服务端没有配置对象存储，暂时用不了这个功能。"
                    + "请不要告诉用户「已生成」，可以改为把内容直接写在回答里。";
        }

        // 文件名由模型生成，去掉路径分隔符再加 UUID 前缀，避免路径穿越和同名覆盖
        String safeName = ToolFileNames.sanitize(fileName);
        if (!StringUtils.hasText(safeName)) {
            safeName = "document.pdf";
        }
        String objectKey = "pdf/" + UUID.randomUUID() + "_" + safeName;

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            try (PdfWriter pdfWriter = new PdfWriter(out);
                 PdfDocument pdf = new PdfDocument(pdfWriter);
                 Document document = new Document(pdf)) {
                document.setFont(cjkFont());
                document.add(new Paragraph(content == null ? "" : content));
            }
            // Document 关闭后内容才 flush 进 out，所以取字节必须放在这里
            byte[] bytes = out.toByteArray();

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("application/pdf");
            metadata.setContentLength(bytes.length);
            oss.get().putObject(oss.bucket(), objectKey, new ByteArrayInputStream(bytes), metadata);

            // 链接的拼法（含那两层编码讲究）统一在 AliyunOssClient#publicUrl 里，
            // 和头像那条路共用一份——两条路各拼一次的话，哪天要改（比如加 CDN 域名）
            // 很容易只改一边
            String url = oss.publicUrl(objectKey);
            log.info("[PDFGenerationTool] 已生成 PDF：{}（{} 字节）", url, bytes.length);

            // 归档到资料库。放在 return 之前、且不参与返回值——
            // 记不上账不该影响用户拿到的这句话（recorder 内部兜住了异常）
            resourceRecorder.record(toolContext, ResourceKind.PDF, safeName, url, objectKey,
                    (long) bytes.length, "application/pdf", null);

            // 说清楚「原样抄进回答」，因为这句话会被模型转述成给用户的答复。
            // 只说「已生成」的话，模型很容易回一句「生成好了」就把链接吞了
            return "PDF 已生成。下载链接如下，请原样抄进最终回答、单独占一行：\n" + url;
        } catch (Exception exception) {
            // 这里必须带上异常信息：上传失败几乎都是凭证、Bucket 名或权限的问题，
            // 只说一句「生成失败」的话，用户和开发者都无从下手
            log.error("[PDFGenerationTool] 生成 PDF 失败", exception);
            return "生成 PDF 失败：" + exception.getMessage() + "。请把失败原因如实告诉用户，不要谎称已生成。";
        }
    }

    /**
     * 中文字体。
     *
     * <p>这里传的 {@code STSongStd-Light} 不是某个字体文件的名字，而是 iText 内置的
     * 「Adobe 亚洲字体包」注册名——它在 {@code font-asian} 包的 {@code cjk_registry.properties}
     * 里登记着，{@code PdfFontFactory} 认得出。看着像 iText 5 的写法，在 iText 8 里依然有效，
     * 前提是 {@code com.itextpdf:font-asian} 在类路径上（由 {@code itext-core} 带进来）。
     *
     * <p>不用系统字体（比如 Windows 的 simsun.ttc）是故意的：那样部署到 Linux 容器里就没有中文字体了，
     * 表现是整份 PDF 的中文全是方框。
     */
    private static PdfFont cjkFont() throws java.io.IOException {
        return PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
    }
}
