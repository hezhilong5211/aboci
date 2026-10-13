package com.aurora.ai.provider;

import com.aurora.ai.provider.support.AiHttpUtil;
import com.aurora.ai.provider.support.RecoverableApiException;
import com.aurora.common.core.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 云雾 GPT-Image client。
 *
 * <p>对接云雾 OpenAI 官方图像端点（modelId 含 gpt-image）:
 * 文生图走 {@code /v1/images/generations};图生图走 {@code /v1/images/edits}(multipart 上传参考图,无需公网图床)。
 * 响应统一三级解析以兼容云雾的大响应体:标准 JSON → 原始字节抠 b64_json → 正则抠 url。</p>
 */
@Component
public class YunwuImageClient {

    /** 请求超时（秒），云雾出图在高负载下可能超过 5 分钟。 */
    private static final int TIMEOUT_SEC = 1000;

    /** 产品侧参考图上限；云雾上游更高，但移动端产品统一限制为 9 张。 */
    private static final int MAX_REFERENCE_IMAGES = 9;

    /** 云雾编辑接口单次图片总大小上限。 */
    private static final long MAX_REFERENCE_BYTES = 50L * 1024L * 1024L;

    /** GPT Image 创建与编辑接口文档声明的提示词字符上限。 */
    private static final int MAX_PROMPT_LENGTH = 32000;

    /** 总像素约束下手动抠 url 的正则 */
    private static final Pattern URL_PATTERN =
            Pattern.compile("\"url\"\\s*:\\s*\"(https?://[^\"]+)\"");

    /**
     * 文生图：调用云雾 /v1/images/generations 并返回图片字节。
     *
     * @param baseUrl 云雾 baseURL（如 https://api.yunwu.ai，自动去末尾斜杠）
     * @param apiKey 鉴权 Token
     * @param modelId 模型 ID（含 gpt-image）
     * @param prompt 提示词
     * @param resolution 分辨率档位（1K/2K/4K，为空默认 1K）
     * @param aspectRatio 宽高比（如 16:9，为空默认 1:1）
     * @return 图片字节
     */
    public byte[] textToImage(String baseUrl, String apiKey, String modelId,
                              String prompt, String resolution, String aspectRatio) {
        if (prompt == null || prompt.isBlank()) {
            throw new ServiceException("云雾 GPT-Image 文生图缺少提示词");
        }
        if (prompt.codePointCount(0, prompt.length()) > MAX_PROMPT_LENGTH) {
            throw new ServiceException("云雾 GPT-Image 文生图提示词不能超过32000个字符");
        }
        // 去末尾斜杠，避免拼出双斜杠
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        // 入参为空时取默认档位
        String res = (resolution == null || resolution.isEmpty()) ? "1K" : resolution;
        String ratio = (aspectRatio == null || aspectRatio.isEmpty()) ? "1:1" : aspectRatio;
        String size = GptImageSize.of(res, ratio);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelId);
        body.put("prompt", prompt);
        body.put("n", 1);
        body.put("size", size);
        body.put("quality", "auto");
        body.put("format", "png");

        byte[] raw;
        try {
            String json = AiHttpUtil.M.writeValueAsString(body);
            raw = AiHttpUtil.postJsonRaw(base + "/v1/images/generations", json, apiKey, TIMEOUT_SEC);
        } catch (Exception e) {
            throw requestFailure("文生图", e);
        }
        return parseImageResponse(raw);
    }

    /**
     * 图生图(image-edit):把参考图作为 multipart 文件上传到云雾 /v1/images/edits,返回图片字节。
     *
     * <p>对齐云雾实测契约:图片字段支持多张(上游最多16张,本产品限制9张)、
     * {@code prompt}、{@code model}、{@code n=1}、{@code size};
     * 单次出 1 张,由调用方循环出 n 张以保持单张容错口径。响应复用 {@link #parseImageResponse}。</p>
     *
     * @param baseUrl     云雾 baseURL（自动去末尾斜杠）
     * @param apiKey      鉴权 Token
     * @param modelId     模型 ID(image-edit,如 gpt-image-2)
     * @param prompt      编辑提示词
     * @param resolution  分辨率档位（1K/2K/4K，为空默认 1K）
     * @param aspectRatio 宽高比（为空默认 1:1）
     * @param refImages   已按执行提示词编号编排的参考图(1~9张)，顺序原样上传，不区分或重排其来源
     * @return 图片字节
     */
    public byte[] imageToImage(String baseUrl, String apiKey, String modelId, String prompt,
                               String resolution, String aspectRatio, List<byte[]> refImages) {
        if (refImages == null || refImages.isEmpty()) {
            throw new ServiceException("图生图缺少参考图");
        }
        // 固定调用时的列表顺序；每个位置对应提示词中的唯一输入编号。
        List<byte[]> orderedReferences = new ArrayList<>(refImages);
        if (orderedReferences.size() > MAX_REFERENCE_IMAGES) {
            throw new ServiceException("图生图参考图最多9张");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new ServiceException("云雾 GPT-Image 图生图缺少提示词");
        }
        if (prompt.codePointCount(0, prompt.length()) > MAX_PROMPT_LENGTH) {
            throw new ServiceException("云雾 GPT-Image 图生图提示词不能超过32000个字符");
        }
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        String res = (resolution == null || resolution.isEmpty()) ? "1K" : resolution;
        String ratio = (aspectRatio == null || aspectRatio.isEmpty()) ? "1:1" : aspectRatio;
        String size = GptImageSize.of(res, ratio);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("prompt", prompt == null ? "" : prompt);
        fields.put("model", modelId);
        fields.put("n", "1");
        fields.put("size", size);
        fields.put("quality", "auto");

        List<AiHttpUtil.FilePart> files = new ArrayList<>(orderedReferences.size());
        // 字段名按 OpenAI images/edits 规范:单图 image、多图每张 image[](原 "file" 被中转以 image is required 拒绝)
        String fieldName = orderedReferences.size() > 1 ? "image[]" : "image";
        long totalBytes = 0L;
        for (int i = 0; i < orderedReferences.size(); i++) {
            byte[] bytes = orderedReferences.get(i);
            if (bytes == null || bytes.length == 0) {
                throw new ServiceException("第" + (i + 1) + "张参考图为空");
            }
            totalBytes += bytes.length;
            if (totalBytes > MAX_REFERENCE_BYTES) {
                throw new ServiceException("图生图参考图总大小不能超过50MB");
            }
            String ct = guessImageContentType(bytes);
            if (ct == null) {
                throw new ServiceException("第" + (i + 1) + "张参考图格式不支持");
            }
            String ext = "image/jpeg".equals(ct) ? ".jpg" : "image/webp".equals(ct) ? ".webp" : ".png";
            files.add(new AiHttpUtil.FilePart(fieldName, "ref" + (i + 1) + ext, ct, bytes));
        }

        byte[] raw;
        try {
            raw = AiHttpUtil.postMultipartRaw(base + "/v1/images/edits", apiKey, TIMEOUT_SEC, fields, files);
        } catch (Exception e) {
            throw requestFailure("图生图", e);
        }
        return parseImageResponse(raw);
    }

    /**
     * 按文件魔数粗判图片 Content-Type(PNG/JPEG/WebP),无法识别返回 null。
     */
    private static String guessImageContentType(byte[] b) {
        if (b == null || b.length < 4) {
            return null;
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) {
            return "image/png";
        }
        if (b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46) {
            return "image/webp";
        }
        return null;
    }

    /**
     * 三级解析响应体提取图片字节：
     * <ol>
     *     <li>标准 JSON：取 data[0].b64_json（base64 解码）或 data[0].url（下载）；</li>
     *     <li>大响应体导致 JSON 解析失败时，直接在原始字节里定位 "b64_json" 手动截取解码；</li>
     *     <li>正则从原始文本抠 "url":"http..." 再下载。</li>
     * </ol>
     * 三级都失败抛 {@link ServiceException}。
     */
    private byte[] parseImageResponse(byte[] data) {
        // 主路径：标准 JSON
        try {
            JsonNode root = AiHttpUtil.M.readTree(data);
            JsonNode payload = root.path("data");
            if (payload.isArray()) {
                payload = payload.path(0);
            }
            String b64 = payload.path("b64_json").asText(null);
            if (b64 != null && !b64.isEmpty()) {
                return Base64.getDecoder().decode(b64);
            }
            String url = payload.path("url").asText(null);
            if (url != null && !url.isEmpty()) {
                return AiHttpUtil.download(url);
            }
        } catch (Exception ignore) {
            // 落到降级
        }

        // 降级一：响应体超大时 JSON 解析失败，直接在原始字节里定位 "b64_json" 再手动截取
        byte[] img = extractB64FromRawBytes(data);
        if (img != null) {
            return img;
        }

        // 降级二：正则从原始文本抠 "url":"http..."
        String text = new String(data, StandardCharsets.UTF_8);
        Matcher mm = URL_PATTERN.matcher(text);
        if (mm.find()) {
            try {
                return AiHttpUtil.download(mm.group(1));
            } catch (Exception e) {
                throw new ServiceException("云雾 GPT-Image 图片下载失败：" + e.getMessage(), e);
            }
        }

        throw new ServiceException("云雾 GPT-Image 响应无法提取图片：" + AiHttpUtil.truncate(text, 200));
    }

    /**
     * 保留上游可恢复错误类型，网络超时/连接中断转为可恢复错误；参数等其他错误立即失败。
     */
    private RuntimeException requestFailure(String operation, Exception error) {
        if (error instanceof RecoverableApiException recoverable) {
            return recoverable;
        }
        if (hasCause(error, InterruptedException.class)) {
            Thread.currentThread().interrupt();
            return new ServiceException("云雾 GPT-Image " + operation + "请求被中断", error);
        }
        if (hasCause(error, IOException.class)) {
            return new RecoverableApiException(0,
                    "云雾 GPT-Image " + operation + "网络异常或超时：" + error.getMessage());
        }
        return new ServiceException("云雾 GPT-Image " + operation + "请求失败：" + error.getMessage(), error);
    }

    /** 判断异常链中是否包含指定类型。 */
    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 在原始字节中定位 "b64_json" 的值并 base64 解码，避免大 JSON 整体解析崩溃。
     *
     * @param data 原始响应字节
     * @return 解码后的图片字节，未找到或解码失败返回 null
     */
    private byte[] extractB64FromRawBytes(byte[] data) {
        byte[] marker = "\"b64_json\"".getBytes(StandardCharsets.UTF_8);
        int idx = indexOf(data, marker, 0);
        if (idx < 0) {
            return null;
        }
        int i = idx + marker.length;
        // 跳过 : 和空白
        while (i < data.length) {
            byte b = data[i];
            if (b == ':' || b == ' ' || b == '\n' || b == '\r' || b == '\t') {
                i++;
            } else {
                break;
            }
        }
        if (i >= data.length || data[i] != '"') {
            return null;
        }
        int start = ++i;
        // base64 不含 "，遇到即结束
        while (i < data.length && data[i] != '"') {
            i++;
        }
        if (i <= start) {
            return null;
        }
        String b64 = new String(data, start, i - start, StandardCharsets.UTF_8);
        try {
            return Base64.getMimeDecoder().decode(b64);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 在字节数组中查找子序列首次出现的下标。
     *
     * @param data 源字节
     * @param pat 待查子序列
     * @param from 起始下标
     * @return 首次出现下标，未找到返回 -1
     */
    private int indexOf(byte[] data, byte[] pat, int from) {
        outer:
        for (int i = from; i <= data.length - pat.length; i++) {
            for (int j = 0; j < pat.length; j++) {
                if (data[i + j] != pat[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
