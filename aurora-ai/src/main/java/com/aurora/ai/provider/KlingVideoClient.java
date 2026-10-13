package com.aurora.ai.provider;

import com.aurora.ai.provider.support.AiHttpUtil;
import com.aurora.common.core.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 可灵 Kling 文生视频客户端
 *
 * <p>异步流程：提交 text2video 任务拿到 task_id，再每 5 秒轮询任务状态，
 * 成功后取首个视频下载 URL。鉴权用 {@link KlingAuth} 现场生成的 JWT（Bearer）。</p>
 *
 * <p>本类只实现文生视频 text2video；图生视频 image2video 需首帧图，暂不实现。</p>
 */
@Component
public class KlingVideoClient {

    /** 轮询间隔（毫秒） */
    private static final long POLL_INTERVAL_MS = 5000L;

    /** 轮询最大次数（5 秒一次，上限约 5 分钟） */
    private static final int MAX_POLL_TIMES = 60;

    /** 提交请求超时（秒） */
    private static final int SUBMIT_TIMEOUT_SEC = 60;

    /** 轮询请求超时（秒） */
    private static final int POLL_TIMEOUT_SEC = 30;

    /**
     * 文生视频：提交任务并轮询直至完成，返回视频下载 URL。
     *
     * @param baseUrl        可灵接口基址，如 https://api.klingai.com（自动去末尾斜杠）
     * @param accessKey      访问密钥（用于生成 JWT）
     * @param secretKey      签名密钥（用于生成 JWT）
     * @param modelName      模型名，如 kling-v1-6 / kling-v2-6 等
     * @param prompt         正向提示词
     * @param durationSec    视频时长（秒），非法时回退默认 5
     * @param aspectRatio    画幅比例，非法时回退默认 16:9
     * @param negativePrompt 负向提示词，非空才提交
     * @return 生成视频的下载 URL
     */
    public String textToVideo(String baseUrl, String accessKey, String secretKey, String modelName,
                              String prompt, int durationSec, String aspectRatio, String negativePrompt) {
        // 基址去末尾斜杠，避免拼接出双斜杠
        String base = stripTrailingSlash(baseUrl);
        // 入参非法时回退默认值
        int duration = durationSec > 0 ? durationSec : 5;
        String ratio = (aspectRatio == null || aspectRatio.isBlank()) ? "16:9" : aspectRatio;

        String taskId = submit(base, accessKey, secretKey, modelName, prompt, duration, ratio, negativePrompt);
        return pollUntilDone(base, accessKey, secretKey, taskId);
    }

    /**
     * 提交 text2video 任务，校验响应 code==0 后返回 task_id。
     */
    private String submit(String base, String accessKey, String secretKey, String modelName,
                          String prompt, int durationSec, String aspectRatio, String negativePrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model_name", modelName);
        body.put("prompt", prompt);
        body.put("duration", String.valueOf(durationSec)); // 文档要求时长为字符串
        body.put("mode", "pro");
        body.put("cfg_scale", 0.5);
        body.put("aspect_ratio", aspectRatio);
        if (negativePrompt != null && !negativePrompt.isEmpty()) {
            body.put("negative_prompt", negativePrompt);
        }

        try {
            String token = KlingAuth.INSTANCE.getToken(accessKey, secretKey);
            String json = AiHttpUtil.M.writeValueAsString(body);
            String respStr = AiHttpUtil.postJson(base + "/v1/videos/text2video", json, token, SUBMIT_TIMEOUT_SEC);

            JsonNode resp = AiHttpUtil.M.readTree(respStr);
            int code = resp.path("code").asInt(-1);
            if (code != 0) {
                throw new ServiceException("可灵提交失败 " + code + ": " + resp.path("message").asText());
            }
            String taskId = resp.path("data").path("task_id").asText(null);
            if (taskId == null || taskId.isBlank()) {
                throw new ServiceException("可灵提交失败：未返回 task_id");
            }
            return taskId;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("可灵提交任务异常: " + e.getMessage(), e);
        }
    }

    /**
     * 轮询任务状态，每 5 秒一次，最多 {@link #MAX_POLL_TIMES} 次。
     * succeed 返回视频 URL，failed 抛异常，超时抛异常。每次轮询前重新生成 JWT（可能过期）。
     */
    private String pollUntilDone(String base, String accessKey, String secretKey, String taskId) {
        String url = base + "/v1/videos/text2video/" + taskId;
        for (int i = 0; i < MAX_POLL_TIMES; i++) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ServiceException("可灵轮询被中断", e);
            }

            JsonNode data;
            try {
                String token = KlingAuth.INSTANCE.getToken(accessKey, secretKey);
                data = AiHttpUtil.M.readTree(AiHttpUtil.get(url, token, POLL_TIMEOUT_SEC)).path("data");
            } catch (Exception e) {
                throw new ServiceException("可灵轮询异常: " + e.getMessage(), e);
            }

            String status = data.path("task_status").asText();
            switch (status) {
                case "submitted":
                case "processing":
                    // 仍在排队 / 处理，继续轮询
                    continue;
                case "succeed":
                    return data.path("task_result").path("videos").path(0).path("url").asText();
                case "failed":
                    throw new ServiceException("可灵生成失败: " + data.path("task_status_msg").asText());
                default:
                    // 未知状态视为仍在进行，继续轮询
                    continue;
            }
        }
        throw new ServiceException("可灵生成超时");
    }

    /** 去掉基址末尾的斜杠 */
    private String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
