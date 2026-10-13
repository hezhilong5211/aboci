package com.aurora.ai.provider;

import com.aurora.ai.domain.SysAiModel;
import com.aurora.common.core.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ResponsesApiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private ExecutorService executor;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldFinishOnCompletedEventWithoutWaitingForConnectionClose() {
        CountDownLatch release = holdStreamOpen("data:{\"type\":\"response.completed\",\"response\":{\"output\":[],\"usage\":{\"input_tokens\":7}}}\n\n");
        try {
            var round = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> new ResponsesApiClient(objectMapper)
                    .streamRound(model(), "secret", objectMapper.createArrayNode(), objectMapper.createArrayNode(),
                            ignored -> {}, ignored -> {}));
            assertEquals(7, round.inputTokens());
        } finally { release.countDown(); }
    }

    @Test
    void shouldTimeOutWhenResponseHeadersArriveButBodyStalls() {
        CountDownLatch release = holdStreamOpen(": keepalive\n\n");
        try {
            ServiceException error = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThrows(ServiceException.class,
                    () -> new ResponsesApiClient(objectMapper).streamRound(model(), "secret",
                            objectMapper.createArrayNode(), objectMapper.createArrayNode(), ignored -> {}, ignored -> {},
                            Duration.ofMillis(100), Duration.ofSeconds(1))));
            assertTrue(error.getMessage().contains("超时"));
        } finally { release.countDown(); }
    }

    @Test
    void shouldRejectIncompleteOutputImmediatelyWithoutSubmittingPartialTools() {
        CountDownLatch release = holdStreamOpen("data:{\"type\":\"response.incomplete\",\"response\":{\"incomplete_details\":{\"reason\":\"max_output_tokens\"}}}\n\n");
        try {
            ServiceException error = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThrows(ServiceException.class,
                    () -> new ResponsesApiClient(objectMapper).streamRound(model(), "secret",
                            objectMapper.createArrayNode(), objectMapper.createArrayNode(), ignored -> {}, ignored -> {})));
            assertTrue(error.getMessage().contains("max_output_tokens"));
        } finally { release.countDown(); }
    }

    @Test
    void shouldRejectClosedStreamWithoutCompletedEvent() {
        server.createContext("/v1/responses", exchange -> {
            byte[] body = ("data:{\"type\":\"response.output_item.done\",\"item\":{\"type\":\"function_call\","
                    + "\"call_id\":\"partial\",\"name\":\"generate_images\",\"arguments\":\"{}\"}}\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        ServiceException error = assertThrows(ServiceException.class, () -> new ResponsesApiClient(objectMapper)
                .streamRound(model(), "secret", objectMapper.createArrayNode(), objectMapper.createArrayNode(), ignored -> {}, ignored -> {}));
        assertTrue(error.getMessage().contains("未收到完整结束事件"));
    }

    private CountDownLatch holdStreamOpen(String body) {
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/v1/responses", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        return release;
    }

    @Test
    void shouldBuildResponsesEndpointFromCommonBaseUrls() {
        assertEquals("https://api.yunwu.ai/v1/responses",
                ResponsesApiClient.endpoint("https://api.yunwu.ai/v1"));
        assertEquals("https://api.yunwu.ai/v1/responses",
                ResponsesApiClient.endpoint("https://api.yunwu.ai"));
        assertEquals("https://example.com/api/v2/responses",
                ResponsesApiClient.endpoint("https://example.com/api/v2/"));
        assertEquals("https://example.com/v1/responses",
                ResponsesApiClient.endpoint("https://example.com/v1/responses"));
    }

    @Test
    void shouldStreamMultimodalInputToolsReasoningAndCachedUsage() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        server.createContext("/v1/responses", exchange -> {
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            assertEquals("Bearer secret", exchange.getRequestHeaders().getFirst("Authorization"));
            String events = "data:{\"type\":\"response.output_text.delta\",\"delta\":\"你好\"}\n\n"
                    + "data:{\"type\":\"response.reasoning_summary_text.delta\",\"delta\":\"分析中\"}\n\n"
                    + "data:{\"type\":\"response.output_item.done\",\"item\":{\"type\":\"function_call\","
                    + "\"call_id\":\"call_1\",\"name\":\"generate_images\",\"arguments\":\"{\\\"jobs\\\":[]}\"}}\n\n"
                    + "data:{\"type\":\"response.completed\",\"response\":{\"output\":["
                    + "{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"你好\"}]},"
                    + "{\"type\":\"function_call\",\"call_id\":\"call_1\",\"name\":\"generate_images\","
                    + "\"arguments\":\"{\\\"jobs\\\":[]}\"}],\"usage\":{\"input_tokens\":120,\"output_tokens\":30,"
                    + "\"input_tokens_details\":{\"cached_tokens\":80}}}}\n\n";
            byte[] bytes = events.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream;charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        ArrayNode input = objectMapper.createArrayNode();
        input.addObject().put("role", "developer").put("content", "系统提示");
        ObjectNode user = input.addObject().put("role", "user");
        ArrayNode content = user.putArray("content");
        content.addObject().put("type", "input_text").put("text", "比较图片1和图片2");
        content.addObject().put("type", "input_image").put("image_url", "data:image/png;base64,AAA=");
        content.addObject().put("type", "input_image").put("image_url", "data:image/jpeg;base64,BBB=");
        ArrayNode tools = objectMapper.createArrayNode();
        tools.addObject().put("type", "function").put("name", "generate_images")
                .set("parameters", objectMapper.createObjectNode().put("type", "object"));

        List<String> text = new ArrayList<>();
        List<String> reasoning = new ArrayList<>();
        ResponsesApiClient.Round round = new ResponsesApiClient(objectMapper)
                .streamRound(model(), "secret", input, tools, text::add, reasoning::add);

        assertEquals(List.of("你好"), text);
        assertEquals(List.of("分析中"), reasoning);
        assertEquals("你好", round.content());
        assertEquals(1, round.toolCalls().size());
        assertEquals("call_1", round.toolCalls().get(0).callId());
        assertEquals("generate_images", round.toolCalls().get(0).name());
        assertEquals(120L, round.inputTokens());
        assertEquals(80L, round.cachedTokens());
        assertEquals(30L, round.outputTokens());

        JsonNode sent = requestBody.get();
        assertTrue(sent.path("stream").asBoolean());
        assertTrue(sent.path("parallel_tool_calls").asBoolean());
        assertEquals("input_image", sent.path("input").path(1).path("content").path(1).path("type").asText());
        assertEquals("data:image/png;base64,AAA=",
                sent.path("input").path(1).path("content").path(1).path("image_url").asText());
        assertEquals("data:image/jpeg;base64,BBB=",
                sent.path("input").path(1).path("content").path(2).path("image_url").asText());
        assertEquals("generate_images", sent.path("tools").path(0).path("name").asText());
    }

    @Test
    void shouldRequestSol61SummaryAndRecoverNonStreamingSummary() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        server.createContext("/v1/responses", exchange -> {
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            byte[] bytes = ("data:{\"type\":\"response.completed\",\"response\":{\"output\":["
                    + "{\"type\":\"reasoning\",\"summary\":[{\"type\":\"summary_text\",\"text\":\"已核对参考图\"}]},"
                    + "{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"可以继续\"}]}]}}\n\n")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        SysAiModel model = model();
        model.setModelDeployment("gpt-6.1-sol");
        List<String> reasoning = new ArrayList<>();
        ResponsesApiClient.Round round = new ResponsesApiClient(objectMapper).streamRound(
                model, "secret", objectMapper.createArrayNode(), objectMapper.createArrayNode(),
                ignored -> {}, reasoning::add);
        assertEquals("可以继续", round.content());
        assertEquals(List.of("已核对参考图"), reasoning);
        assertEquals("auto", requestBody.get().path("reasoning").path("summary").asText());
        assertFalse(requestBody.get().path("store").asBoolean(true));
    }

    @Test
    void shouldSurfaceResponsesFailureEvent() {
        server.createContext("/v1/responses", exchange -> {
            byte[] bytes = ("data:{\"type\":\"response.failed\",\"response\":{\"error\":{"
                    + "\"message\":\"upstream rejected\"}}}\n\n").getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (Exception ignored) {
                // 测试服务端仅负责返回固定失败帧。
            } finally {
                exchange.close();
            }
        });

        ServiceException error = assertThrows(ServiceException.class, () ->
                new ResponsesApiClient(objectMapper).streamRound(
                        model(), "secret", objectMapper.createArrayNode(), objectMapper.createArrayNode(),
                        ignored -> { }, ignored -> { }));
        assertEquals("upstream rejected", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldReplayToolRoundThroughStrictRelayWithoutLosingContext(boolean completedHasOutput) throws Exception {
        ArrayNode output = (ArrayNode) objectMapper.readTree("""
                [
                  {"type":"reasoning","id":"rs_1","status":"completed",
                   "summary":[{"type":"summary_text","text":"已分析参考图"}],"encrypted_content":"opaque-reasoning"},
                  {"type":"function_call","id":"fc_1","status":"completed","call_id":"call_1",
                   "name":"generate_images","arguments":"{}"},
                  {"type":"message","id":"msg_1","role":"assistant","status":"completed",
                   "content":[{"type":"output_text","text":"正在生成","annotations":[]}]}
                ]
                """);
        ((ObjectNode) output.get(1)).put("arguments", "{\"status\":\"requested\"}");
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<JsonNode> replay = new AtomicReference<>();
        server.createContext("/v1/responses", exchange -> {
            JsonNode body = objectMapper.readTree(exchange.getRequestBody());
            int requestNumber = requests.incrementAndGet();
            String events;
            int httpStatus = 200;
            if (requestNumber == 1) {
                StringBuilder stream = new StringBuilder();
                for (JsonNode item : output) {
                    stream.append("data:").append(objectMapper.createObjectNode()
                            .put("type", "response.output_item.done").set("item", item).toString()).append("\n\n");
                }
                ObjectNode response = objectMapper.createObjectNode();
                response.set("output", completedHasOutput ? output : objectMapper.createArrayNode());
                response.putObject("usage").put("input_tokens", 12).put("output_tokens", 8);
                stream.append("data:").append(objectMapper.createObjectNode()
                        .put("type", "response.completed").set("response", response).toString()).append("\n\n");
                events = stream.toString();
            } else {
                replay.set(body);
                if (body.path("input").path(5).has("status")) {
                    httpStatus = 400;
                    events = "{\"error\":{\"type\":\"invalid_request_error\","
                            + "\"message\":\"Unknown parameter: 'input[5].status'.\"}}";
                } else {
                    events = "data:{\"type\":\"response.output_text.delta\",\"delta\":\"已处理\"}\n\n"
                            + "data:{\"type\":\"response.completed\",\"response\":{\"output\":[],"
                            + "\"usage\":{\"input_tokens\":20,\"output_tokens\":3}}}\n\n";
                }
            }
            byte[] bytes = events.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(httpStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        ArrayNode input = objectMapper.createArrayNode();
        input.addObject().put("role", "developer").put("content", "系统提示");
        input.addObject().put("role", "user").put("content", "上次生成失败");
        input.addObject().put("role", "assistant").put("content", "请重新描述需求");
        input.addObject().put("role", "user").putArray("content")
                .addObject().put("type", "input_image").put("image_url", "data:image/png;base64,AAA=");
        ArrayNode tools = objectMapper.createArrayNode();
        tools.addObject().put("type", "function").put("name", "generate_images")
                .putObject("parameters").put("type", "object");
        ResponsesApiClient client = new ResponsesApiClient(objectMapper);
        ResponsesApiClient.Round first = client.streamRound(model(), "secret", input, tools, t -> {}, t -> {});
        assertEquals(output, first.outputItems());
        assertEquals("call_1", first.toolCalls().get(0).callId());
        for (JsonNode item : first.outputItems()) input.add(item.deepCopy());
        String result = "{\"status\":\"FAILED\",\"message\":\"图片生成失败\"}";
        input.addObject().put("type", "function_call_output").put("call_id", "call_1")
                .put("status", "completed").put("output", result);
        ArrayNode originalInput = input.deepCopy();

        ResponsesApiClient.Round second = client.streamRound(model(), "secret", input, tools, t -> {}, t -> {});
        assertEquals("已处理", second.content());
        assertEquals(20L, second.inputTokens());
        assertEquals(3L, second.outputTokens());
        assertEquals(2, requests.get());
        assertEquals(originalInput, input, "发送请求不能修改调用方的上下文");
        assertEquals(output, first.outputItems(), "保留原始模型返回供后续处理");
        JsonNode sent = replay.get().path("input");
        for (int i = 0; i < input.size(); i++) {
            ObjectNode expected = input.get(i).deepCopy();
            expected.remove("status");
            assertEquals(expected, sent.get(i), "除顶层状态外，所有上下文必须完整保留，索引=" + i);
            assertFalse(sent.get(i).has("status"));
        }
        assertEquals(result, sent.get(7).path("output").asText());
        assertEquals(tools, replay.get().path("tools"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldSurfaceStreamErrorImmediatelyWithoutReturningPartialTools(boolean nestedError) {
        ObjectNode detail = objectMapper.createObjectNode()
                .put("code", "invalid_request_error")
                .put("message", "Unknown parameter: 'input[5].status'.");
        ObjectNode event = objectMapper.createObjectNode().put("type", "error");
        if (nestedError) event.set("error", detail);
        else event.setAll(detail);
        CountDownLatch release = holdStreamOpen(
                "data:{\"type\":\"response.output_item.done\",\"item\":{\"type\":\"function_call\","
                        + "\"call_id\":\"partial\",\"name\":\"generate_images\",\"arguments\":\"{}\"}}\n\n"
                        + "data:" + event + "\n\n");
        try {
            ServiceException error = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThrows(
                    ServiceException.class, () -> new ResponsesApiClient(objectMapper).streamRound(
                            model(), "secret", objectMapper.createArrayNode(), objectMapper.createArrayNode(),
                            t -> {}, t -> {})));
            assertEquals("Unknown parameter: 'input[5].status'.", error.getMessage());
        } finally { release.countDown(); }
    }

    private SysAiModel model() {
        SysAiModel model = new SysAiModel();
        model.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        model.setModelDeployment("gpt-5.6-sol");
        model.setMaxTokens(8192);
        return model;
    }
}
