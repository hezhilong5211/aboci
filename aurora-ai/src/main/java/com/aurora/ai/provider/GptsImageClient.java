package com.aurora.ai.provider;

import com.aurora.ai.provider.support.AiHttpUtil;
import com.aurora.common.core.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GPTS API Image（gptsapi.net）文生图 client。
 *
 * <p>与云雾的同步调用不同，GPTS 采用「异步提交拿 result_id → 轮询查结果」的三段式架构：
 * 先 POST 提交任务取回 result_id，再按固定间隔轮询查询结果，直到完成 / 失败 / 超时。</p>
 *
 * <p>本类只实现文生图（text-to-image）。图生图（image-edit）的参考图需先上传到公网图床
 * 换取 URL，本阶段不实现。</p>
 */
@Component
public class GptsImageClient {

    /** 默认分辨率 */
    private static final String DEFAULT_RESOLUTION = "1K";

    /** 默认宽高比 */
    private static final String DEFAULT_ASPECT_RATIO = "1:1";

    /** 提交 / 轮询请求超时（秒） */
    private static final int TIMEOUT_SEC = 30;

    /** 轮询间隔（毫秒） */
    private static final long POLL_INTERVAL_MS = 3000L;

    /** 轮询最大次数 */
    private static final int MAX_POLL_TIMES = 120;

    /**
     * 文生图：提交任务后轮询获取生成结果，返回图片字节。
     *
     * @param baseUrl     接口基地址（如 https://api.gptsapi.net），末尾斜杠会被去除
     * @param apiKey      Bearer 鉴权 Token
     * @param modelId     模型 ID（如 gpt-image-2-plus）
     * @param prompt      文生图提示词
     * @param resolution  分辨率，为空时默认 "1K"
     * @param aspectRatio 宽高比，为空时默认 "1:1"
     * @return 生成图片的字节内容
     */
    public byte[] textToImage(String baseUrl, String apiKey, String modelId, String prompt,
                              String resolution, String aspectRatio) {
        // 去除 baseUrl 末尾斜杠，避免拼接出双斜杠
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        // 入参兜底默认值
        String res = (resolution == null || resolution.isEmpty()) ? DEFAULT_RESOLUTION : resolution;
        String ratio = (aspectRatio == null || aspectRatio.isEmpty()) ? DEFAULT_ASPECT_RATIO : aspectRatio;

        String resultId = submit(base, apiKey, modelId, prompt, res, ratio);
        return poll(base, apiKey, resultId);
    }

    /**
     * 提交文生图任务，从响应 data.id 取回 result_id。
     */
    private String submit(String base, String apiKey, String modelId, String prompt,
                          String resolution, String aspectRatio) {
        String url = base + "/api/v3/openai/" + modelId + "/text-to-image";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", prompt);
        body.put("aspect_ratio", aspectRatio);
        body.put("resolution", resolution);
        try {
            String json = AiHttpUtil.M.writeValueAsString(body);
            String resp = AiHttpUtil.postJson(url, json, apiKey, TIMEOUT_SEC);
            JsonNode id = AiHttpUtil.M.readTree(resp).path("data").path("id");
            if (id.isMissingNode() || id.asText().isEmpty()) {
                throw new ServiceException("GPTS 文生图提交失败，无法解析 result_id: " + AiHttpUtil.truncate(resp, 300));
            }
            return id.asText();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("GPTS 文生图提交请求异常: " + e.getMessage(), e);
        }
    }

    /**
     * 轮询查询任务结果：每 3 秒一次，最多 120 次。
     *
     * <p>status 为 completed / succeeded 时取 data.outputs[0]：以 http(s):// 开头则下载，
     * 否则按 base64 解码（可能带 data:image/...;base64, 前缀，去掉逗号前的前缀）。
     * status 为 failed / error 时抛业务异常；超过最大次数仍未完成视为超时。</p>
     */
    private byte[] poll(String base, String apiKey, String resultId) {
        String url = base + "/api/v3/predictions/" + resultId + "/result";
        try {
            for (int i = 0; i < MAX_POLL_TIMES; i++) {
                Thread.sleep(POLL_INTERVAL_MS);
                JsonNode data = AiHttpUtil.M.readTree(AiHttpUtil.get(url, apiKey, TIMEOUT_SEC)).path("data");
                String status = data.path("status").asText();
                switch (status) {
                    case "completed":
                    case "succeeded": {
                        String out = data.path("outputs").path(0).asText("");
                        if (out.isEmpty()) {
                            throw new ServiceException("GPTS 文生图结果为空");
                        }
                        // outputs 为 URL，直接下载
                        if (out.startsWith("http://") || out.startsWith("https://")) {
                            return AiHttpUtil.download(out);
                        }
                        // outputs 为 base64，去掉可能存在的 data:image/...;base64, 前缀后解码
                        int comma = out.indexOf(',');
                        String b64 = comma >= 0 ? out.substring(comma + 1) : out;
                        return Base64.getDecoder().decode(b64);
                    }
                    case "failed":
                    case "error":
                        throw new ServiceException("GPTS 文生图生成失败: " + data.path("error").asText("生成失败"));
                    default:
                        // created / processing / starting 等中间态，继续轮询
                        continue;
                }
            }
            throw new ServiceException("GPTS 文生图生成超时");
        } catch (ServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            // 恢复中断标志，避免吞掉中断信号
            Thread.currentThread().interrupt();
            throw new ServiceException("GPTS 文生图轮询被中断", e);
        } catch (Exception e) {
            throw new ServiceException("GPTS 文生图轮询请求异常: " + e.getMessage(), e);
        }
    }
}
