package com.aurora.ai.provider;

import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.provider.support.AiHttpUtil;
import com.aurora.common.core.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * OpenAI Responses 兼容协议客户端。
 * 仅承载本产品已有入口需要的文本、图片、流式输出和函数工具调用，不接入托管工具。
 */
@Component
@RequiredArgsConstructor
public class ResponsesApiClient {

    private static final int STREAM_TIMEOUT_SECONDS = 180;

    private final ObjectMapper objectMapper;

    /** 使用真实 Responses 文本请求测试模型配置，避免误走 Chat Completions。 */
    public String testConnection(SysAiModel model, String apiKey) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model.getModelDeployment());
        body.put("input", "只回复 pong");
        body.put("max_output_tokens", 64);

        HttpRequest request = request(model, apiKey, body);
        HttpResponse<String> response = AiHttpUtil.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertSuccess(response.statusCode(), response.body());
        JsonNode root = objectMapper.readTree(response.body());
        String text = extractOutputText(root.path("output"));
        if (!StringUtils.hasText(text)) {
            throw new ServiceException("Responses 未返回文本内容");
        }
        return text;
    }

    /** 发起一轮 Responses SSE 请求并解析正文、思考、工具调用、回灌项和 token。 */
    public Round streamRound(SysAiModel model, String apiKey, ArrayNode input, ArrayNode tools,
                             Consumer<String> onText, Consumer<String> onReasoning) throws Exception {
        return streamRound(model, apiKey, input, tools, onText, onReasoning,
                Duration.ofSeconds(STREAM_TIMEOUT_SECONDS), Duration.ofMinutes(10));
    }

    Round streamRound(SysAiModel model, String apiKey, ArrayNode input, ArrayNode tools,
                      Consumer<String> onText, Consumer<String> onReasoning,
                      Duration idleTimeout, Duration totalTimeout) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model.getModelDeployment());
        body.set("input", toRequestInput(input));
        body.put("stream", true);
        if (model.getMaxTokens() != null && model.getMaxTokens() > 0) {
            body.put("max_output_tokens", model.getMaxTokens());
        }
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
            body.put("parallel_tool_calls", true);
        }

        HttpRequest request = request(model, apiKey, body);
        HttpResponse<Stream<String>> response = AiHttpUtil.CLIENT.send(request, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorBody;
            try (Stream<String> lines = response.body()) {
                errorBody = lines.reduce("", (left, right) -> left + right);
            }
            assertSuccess(response.statusCode(), errorBody);
        }

        StringBuilder content = new StringBuilder();
        ArrayNode completedOutput = objectMapper.createArrayNode();
        ArrayNode fallbackOutput = objectMapper.createArrayNode();
        long inputTokens = 0;
        long outputTokens = 0;
        long cachedTokens = 0;
        boolean receivedCompletion = false;
        long started = System.nanoTime();
        AtomicLong lastEvent = new AtomicLong(started);
        AtomicBoolean timedOut = new AtomicBoolean();
        // HttpRequest.timeout 只覆盖响应头等待；流式响应体必须独立限制空闲及总时长。
        var watchdog = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("responses-read-timeout").factory());
        long tickMillis = Math.max(10, Math.min(1000, idleTimeout.toMillis()));
        var timeout = watchdog.scheduleAtFixedRate(() -> {
            long now = System.nanoTime();
            if (now - lastEvent.get() >= idleTimeout.toNanos() || now - started >= totalTimeout.toNanos()) {
                timedOut.set(true);
                response.body().close();
            }
        }, tickMillis, tickMillis, TimeUnit.MILLISECONDS);
        try (Stream<String> lines = response.body()) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring("data:".length()).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) break;
                JsonNode event = objectMapper.readTree(data);
                lastEvent.set(System.nanoTime());
                String type = event.path("type").asText("");
                if ("response.output_text.delta".equals(type)) {
                    String delta = event.path("delta").asText("");
                    if (!delta.isEmpty()) {
                        content.append(delta);
                        onText.accept(delta);
                    }
                } else if ("response.reasoning_summary_text.delta".equals(type)
                        || "response.reasoning_text.delta".equals(type)) {
                    String delta = event.path("delta").asText("");
                    if (!delta.isEmpty()) {
                        onReasoning.accept(delta);
                    }
                } else if ("response.output_item.done".equals(type) && event.path("item").isObject()) {
                    fallbackOutput.add(event.path("item").deepCopy());
                } else if ("response.completed".equals(type)) {
                    JsonNode completed = event.path("response");
                    JsonNode output = completed.path("output");
                    if (output.isArray()) {
                        completedOutput.removeAll();
                        completedOutput.addAll((ArrayNode) output.deepCopy());
                    }
                    JsonNode usage = completed.path("usage");
                    inputTokens = usage.path("input_tokens").asLong(0);
                    outputTokens = usage.path("output_tokens").asLong(0);
                    cachedTokens = usage.path("input_tokens_details").path("cached_tokens").asLong(0);
                    receivedCompletion = true;
                    break; // 终态即结束，不依赖中转服务关闭 HTTP 连接。
                } else if ("error".equals(type)) {
                    // 标准 Responses 错误在事件顶层；部分中转站放在 error 对象内。
                    JsonNode error = event.path("error").isObject() ? event.path("error") : event;
                    throw new ServiceException(error.path("message").asText("Responses 请求失败"));
                } else if ("response.failed".equals(type)) {
                    String message = event.path("response").path("error").path("message").asText("Responses 请求失败");
                    throw new ServiceException(message);
                } else if ("response.incomplete".equals(type)) {
                    throw new ServiceException("模型输出未完成（"
                            + event.path("response").path("incomplete_details").path("reason").asText("unknown")
                            + "），本轮计划未提交，请重试或检查模型输出上限");
                }
            }
        } catch (RuntimeException e) {
            if (timedOut.get()) throw new ServiceException("模型流式响应超时，本轮计划未提交，请稍后重试");
            throw e;
        } finally {
            timeout.cancel(false);
            watchdog.shutdownNow();
        }
        if (timedOut.get()) throw new ServiceException("模型流式响应超时，本轮计划未提交，请稍后重试");
        if (!receivedCompletion) throw new ServiceException("模型响应中断，未收到完整结束事件，本轮计划未提交");

        ArrayNode outputItems = completedOutput.isEmpty() ? fallbackOutput : completedOutput;
        if (content.isEmpty()) {
            content.append(extractOutputText(outputItems));
        }
        List<ToolCall> toolCalls = extractToolCalls(outputItems);
        return new Round(content.toString(), toolCalls, outputItems,
                inputTokens, outputTokens, cachedTokens);
    }

    /**
     * 回灌项的 status 是可选协议状态，部分中转站返回它但不接受再次作为输入。
     * 在发送边界仅移除已支持项的顶层 status，保留 id、推理、图片与调用关联信息。
     * 不递归清理，避免破坏 arguments/output 中的业务状态；不修改调用方上下文或原始响应。
     */
    private ArrayNode toRequestInput(ArrayNode input) {
        ArrayNode requestInput = input.deepCopy();
        for (JsonNode item : requestInput) {
            switch (item.path("type").asText("")) {
                case "message", "reasoning", "function_call", "function_call_output" ->
                        ((ObjectNode) item).remove("status");
                default -> { } // 不改写其他类型，避免移除未来工具的必填状态。
            }
        }
        return requestInput;
    }

    /** 根据后台 Base URL 拼接 Responses 地址。 */
    public static String endpoint(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        if (base.endsWith("/responses")) {
            return base;
        }
        if (base.matches(".*\\/v\\d+$") || base.matches(".*\\/api\\/v\\d+$")) {
            return base + "/responses";
        }
        return base + "/v1/responses";
    }

    private HttpRequest request(SysAiModel model, String apiKey, ObjectNode body) throws Exception {
        return HttpRequest.newBuilder(URI.create(endpoint(model.getBaseUrl())))
                .timeout(Duration.ofSeconds(STREAM_TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
    }

    private void assertSuccess(int statusCode, String responseBody) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        String body = responseBody == null ? "" : responseBody;
        if (body.length() > 500) {
            body = body.substring(0, 500) + "...";
        }
        throw new ServiceException("Responses 请求失败 HTTP " + statusCode + ": " + body);
    }

    private String extractOutputText(JsonNode output) {
        if (!output.isArray()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : output) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            for (JsonNode part : item.path("content")) {
                if ("output_text".equals(part.path("type").asText())) {
                    text.append(part.path("text").asText(""));
                }
            }
        }
        return text.toString();
    }

    private List<ToolCall> extractToolCalls(ArrayNode output) {
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode item : output) {
            if (!"function_call".equals(item.path("type").asText())) {
                continue;
            }
            String callId = item.path("call_id").asText(null);
            String name = item.path("name").asText(null);
            if (StringUtils.hasText(callId) && StringUtils.hasText(name)) {
                calls.add(new ToolCall(callId, name, item.path("arguments").asText("{}")));
            }
        }
        return calls;
    }

    public record ToolCall(String callId, String name, String arguments) {
    }

    public record Round(String content, List<ToolCall> toolCalls, ArrayNode outputItems,
                        long inputTokens, long outputTokens, long cachedTokens) {
    }
}
