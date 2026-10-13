package com.aurora.ai.provider;

import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.provider.support.AiHttpUtil;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.storage.ProfileStorage;
import com.aurora.framework.utils.EncryptUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Gemini 视觉识别 client（云雾 Gemini 原生 generateContent，裸 HTTP，同步）。
 *
 * <p>DeepSeek 是文本模型不能直接读图，Agent 需要「看图」时调本 client：先把图片下载并转 base64，
 * 按 Gemini 原生格式（contents[].parts[] 含 inline_data 图片 + text 问题）同步请求云雾
 * {@code /v1beta/models/{model}:generateContent}，返回模型对图片的文字描述。</p>
 *
 * <p>失败抛 {@link ServiceException}，由上层（Agent 工具调度）捕获后降级为「视觉识别失败」回灌，
 * 不中断整轮对话。</p>
 */
@Component
public class GeminiVisionClient {

    /** 请求超时（秒）：视觉 base64 payload 较大，给足 60 秒 */
    private static final int TIMEOUT_SEC = 60;

    private final EncryptUtils encryptUtils;
    private final ProfileStorage profileStorage;

    public GeminiVisionClient(EncryptUtils encryptUtils, ProfileStorage profileStorage) {
        this.encryptUtils = encryptUtils;
        this.profileStorage = profileStorage;
    }

    /**
     * 视觉识别：下载图片转 base64，请求云雾视觉模型，返回文字描述。
     *
     * @param visionModel 视觉模型配置（sys_ai_model 中 model_type='vision' 的行）
     * @param imageUrl    已上传图片的可访问 URL
     * @param intent      识别意图 / 要回答的问题，作为 text 部分
     * @return 视觉识别结果（文字描述 + 本次 usage token，供上层计费按当轮累加）
     */
    public VisionResult recognize(SysAiModel visionModel, String imageUrl, String intent) {
        try {
            // 1. 取图片字节并转 base64:/profile 相对地址读本地文件(云雾 gemini 访问不到本机,必须内联 base64),http(s) 则下载
            byte[] img = loadImageBytes(imageUrl);
            return recognizeBytes(visionModel, img, guessMimeType(img), intent);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("Gemini 视觉识别请求异常: " + e.getMessage(), e);
        }
    }

    /** 用真实图片探针测试 Agent 使用的 Gemini 原生多模态链路。 */
    public VisionResult testConnection(SysAiModel visionModel) {
        try {
            BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.RED);
                graphics.fillRect(0, 0, 16, 32);
                graphics.setColor(Color.BLUE);
                graphics.fillRect(16, 0, 16, 32);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return recognizeBytes(visionModel, out.toByteArray(), "image/png", "请简短描述图片中的颜色");
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("视觉模型图片探针生成失败", e);
        }
    }

    /** 发送内联图片字节，供真实识图与管理后台多模态探针复用。 */
    private VisionResult recognizeBytes(SysAiModel visionModel, byte[] img, String mime, String intent) {
        try {
            String b64 = Base64.getEncoder().encodeToString(img);

            // 2. 解密 API Key（云雾示例同时用 query ?key= 与 Authorization Bearer 鉴权，两处都用解密后的 key）
            String apiKey = encryptUtils.decrypt(visionModel.getApiKey());

            // 3. 构造 Gemini 原生 generateContent 请求体：contents[].parts[]（inline_data 图片 + text 问题）
            ObjectNode body = AiHttpUtil.M.createObjectNode();
            ArrayNode contents = body.putArray("contents");
            ObjectNode userMsg = contents.addObject();
            userMsg.put("role", "user");
            ArrayNode parts = userMsg.putArray("parts");
            ObjectNode inlineData = parts.addObject().putObject("inline_data");
            inlineData.put("mime_type", mime);
            inlineData.put("data", b64);
            parts.addObject().put("text", intent);
            String json = AiHttpUtil.M.writeValueAsString(body);

            // 4. 拼接 Gemini 原生端点：{base}/v1beta/models/{model}:generateContent?key={apiKey}
            String url = buildGeminiUrl(visionModel.getBaseUrl(), visionModel.getModelDeployment(), apiKey);

            // 5. 同步请求并解析 candidates[0].content.parts[*].text（拼接所有 text 片段）
            String resp = AiHttpUtil.postJson(url, json, apiKey, TIMEOUT_SEC);
            JsonNode root = AiHttpUtil.M.readTree(resp);
            JsonNode partsNode = root.path("candidates").path(0).path("content").path("parts");
            StringBuilder sb = new StringBuilder();
            if (partsNode.isArray()) {
                for (JsonNode p : partsNode) {
                    String t = p.path("text").asText("");
                    if (!t.isEmpty()) {
                        sb.append(t);
                    }
                }
            }
            if (sb.length() == 0) {
                throw new ServiceException("Gemini 视觉识别响应无法解析: " + AiHttpUtil.truncate(resp, 300));
            }
            // 6. 解析顶层 usageMetadata 的 token（缺失当 0，供上层计费按当轮累加）
            JsonNode usage = root.path("usageMetadata");
            long promptTokens = usage.path("promptTokenCount").asLong(0);
            long candidatesTokens = usage.path("candidatesTokenCount").asLong(0);
            return new VisionResult(sb.toString(), promptTokens, candidatesTokens);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("Gemini 视觉识别请求异常: " + e.getMessage(), e);
        }
    }

    /**
     * 取图片字节:/profile 相对地址映射到本地上传目录读取(防 .. 穿越);http(s) 绝对地址走下载。
     * 之所以读本地:云雾 gemini 服务端无法访问开发机 localhost 的相对图地址,必须把图字节内联 base64 传过去。
     */
    private byte[] loadImageBytes(String imageUrl) throws Exception {
        if (imageUrl != null && imageUrl.startsWith("/profile/") && !imageUrl.contains("..")) {
            return profileStorage.read(imageUrl);
        }
        // 绝对 URL(http/https):下载
        return AiHttpUtil.download(imageUrl);
    }

    /**
     * 按文件魔数粗判图片 mime_type(PNG/JPEG/WebP/GIF),无法识别默认 image/png。
     */
    private static String guessMimeType(byte[] b) {
        if (b == null || b.length < 4) {
            return "image/png";
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
        if (b[0] == 0x47 && b[1] == 0x49 && b[2] == 0x46) {
            return "image/gif";
        }
        return "image/png";
    }

    /**
     * 拼接 Gemini 原生 generateContent 端点地址：{base}/v1beta/models/{model}:generateContent?key={apiKey}。
     * 云雾 Gemini 视觉走 Gemini 原生协议（非 OpenAI 兼容），API Key 经 query 参数传递；先去 base 末尾斜杠避免双斜杠。
     */
    private String buildGeminiUrl(String baseUrl, String model, String apiKey) {
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        return base + "/v1beta/models/" + model + ":generateContent?key=" + apiKey;
    }

    /**
     * 视觉识别结果：文字描述 + 本次 usage token。
     * 上层（AgentService）用 {@code description} 回灌对话，用 token 计入当轮计费。
     */
    public static final class VisionResult {
        /** 模型对图片的文字描述（candidates[0].content.parts[*].text 拼接） */
        private final String description;
        /** 输入 token（usageMetadata.promptTokenCount，缺失为 0） */
        private final long promptTokens;
        /** 输出 token（usageMetadata.candidatesTokenCount，缺失为 0） */
        private final long candidatesTokens;

        public VisionResult(String description, long promptTokens, long candidatesTokens) {
            this.description = description;
            this.promptTokens = promptTokens;
            this.candidatesTokens = candidatesTokens;
        }

        public String getDescription() {
            return description;
        }

        public long getPromptTokens() {
            return promptTokens;
        }

        public long getCandidatesTokens() {
            return candidatesTokens;
        }
    }
}
