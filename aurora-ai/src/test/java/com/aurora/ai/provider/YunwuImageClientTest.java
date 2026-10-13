package com.aurora.ai.provider;

import com.aurora.ai.provider.support.RecoverableApiException;
import com.aurora.common.core.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YunwuImageClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldCallCreateEndpointWithDocumentedFieldsAndParseArrayData() throws Exception {
        byte[] expected = "text-image".getBytes(StandardCharsets.UTF_8);
        String prompt = "图".repeat(32000);
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/images/generations", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respondJson(exchange, 200, "{\"data\":[{\"b64_json\":\""
                    + Base64.getEncoder().encodeToString(expected) + "\"}]}");
        });

        byte[] actual = new YunwuImageClient().textToImage(
                baseUrl, "key", "gpt-image-2", prompt, "1K", "1:1");

        assertArrayEquals(expected, actual);
        JsonNode body = MAPPER.readTree(requestBody.get());
        assertEquals("gpt-image-2", body.path("model").asText());
        assertEquals(prompt, body.path("prompt").asText());
        assertEquals(1, body.path("n").asInt());
        assertEquals("1024x1024", body.path("size").asText());
        assertEquals("auto", body.path("quality").asText());
        assertEquals("png", body.path("format").asText());
    }

    @Test
    void shouldCallEditEndpointInReferenceOrderAndParseObjectData() {
        byte[] expected = "edited-image".getBytes(StandardCharsets.UTF_8);
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/v1/images/edits", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            respondJson(exchange, 200, "{\"data\":{\"b64_json\":\""
                    + Base64.getEncoder().encodeToString(expected) + "\"}}");
        });

        byte[] actual = new YunwuImageClient().imageToImage(
                baseUrl, "key", "gpt-image-2", "融合两张图片", "2K", "3:4",
                List.of(pngBytes(1), pngBytes(2)));

        assertArrayEquals(expected, actual);
        assertTrue(contentType.get().startsWith("multipart/form-data; boundary="));
        String multipart = requestBody.get();
        assertTrue(multipart.contains("name=\"model\""));
        assertTrue(multipart.contains("gpt-image-2"));
        assertTrue(multipart.contains("name=\"quality\""));
        assertTrue(multipart.contains("auto"));
        assertTrue(multipart.contains("name=\"image[]\"; filename=\"ref1.png\""));
        assertTrue(multipart.contains("name=\"image[]\"; filename=\"ref2.png\""));
        assertTrue(multipart.indexOf("ref1.png") < multipart.indexOf("ref2.png"));
        assertFalse(multipart.contains("name=\"mask\""));
    }

    @Test
    void shouldRejectInvalidPromptAndReferenceLimitsBeforeCallingUpstream() {
        YunwuImageClient client = new YunwuImageClient();
        assertThrows(ServiceException.class, () -> client.textToImage(
                baseUrl, "key", "gpt-image-2", "a".repeat(32001), "1K", "1:1"));
        assertThrows(ServiceException.class, () -> client.imageToImage(
                baseUrl, "key", "gpt-image-2", "a".repeat(32001), "1K", "1:1", List.of(pngBytes(1))));

        List<byte[]> tenImages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tenImages.add(pngBytes(i));
        }
        assertThrows(ServiceException.class, () -> client.imageToImage(
                baseUrl, "key", "gpt-image-2", "编辑", "1K", "1:1", tenImages));

        byte[] first = new byte[26 * 1024 * 1024];
        byte[] second = new byte[26 * 1024 * 1024];
        markPng(first);
        markPng(second);
        assertThrows(ServiceException.class, () -> client.imageToImage(
                baseUrl, "key", "gpt-image-2", "编辑", "1K", "1:1", List.of(first, second)));
    }

    @Test
    void shouldPreserveRecoverableUpstreamStatus() {
        server.createContext("/v1/images/generations", exchange -> respondJson(exchange, 504, "upstream timeout"));
        assertThrows(RecoverableApiException.class, () -> new YunwuImageClient().textToImage(
                baseUrl, "key", "gpt-image-2", "生成图片", "1K", "1:1"));
    }

    private static byte[] pngBytes(int marker) {
        byte[] bytes = new byte[16];
        markPng(bytes);
        bytes[8] = (byte) marker;
        return bytes;
    }

    private static void markPng(byte[] bytes) {
        bytes[0] = (byte) 0x89;
        bytes[1] = 0x50;
        bytes[2] = 0x4E;
        bytes[3] = 0x47;
    }

    private static void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
