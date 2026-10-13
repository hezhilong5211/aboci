package com.aurora.ai.provider;

import com.aurora.ai.provider.support.AiHttpUtil;
import com.aurora.ai.provider.support.RecoverableApiException;
import com.aurora.common.core.exception.ServiceException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LuxaiImageClientTest {
    private HttpServer server;
    private String base;
    private final LuxaiImageClient client = new LuxaiImageClient();
    private final AtomicReference<String> request = new AtomicReference<>();
    private static final byte[] OUTPUT = {1, 2, 3, 4};

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/images/generations", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer local-test-key");
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("{\"data\":[{\"b64_json\":\"" + Base64.getEncoder().encodeToString(OUTPUT) + "\"}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/v1/images/edits", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] response = "{\"data\":[{\"url\":\"http://127.0.0.1:".concat(Integer.toString(server.getAddress().getPort()))
                    .concat("/output.png\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/output.png", exchange -> {
            exchange.sendResponseHeaders(200, OUTPUT.length);
            exchange.getResponseBody().write(OUTPUT);
            exchange.close();
        });
        server.createContext("/failure/v1/images/generations", exchange -> {
            byte[] response = "private-channel-secret".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void generatesUsingV1BaseAndExactAspectRatioAndFixedQuality() throws Exception {
        assertThat(client.textToImage(base + "/v1/", "local-test-key", "gpt-image-2.5-flare", "white cup",
                "1K", "16:9", "high")).containsExactly(OUTPUT);
        var body = AiHttpUtil.M.readTree(request.get());
        assertThat(body.path("size").asText()).isEqualTo("1280x720");
        assertThat(body.path("model").asText()).isEqualTo("gpt-image-2.5-flare");
        assertThat(body.path("quality").asText()).isEqualTo("high");
        assertThat(body.path("n").asInt()).isOne();
    }

    @Test
    void preservesMultipartReferencesInDeclaredOrderAndDownloadsUrl() {
        byte[] first = {(byte) 137, 80, 78, 71, 11, 22, 33};
        byte[] second = {(byte) 255, (byte) 216, 77, 88, 99};
        assertThat(client.imageToImage(base, "local-test-key", "gpt-image-2.5-sunburst", "combine references",
                "4K", "3:4", "medium", List.of(first, second))).containsExactly(OUTPUT);
        String multipart = request.get();
        assertThat(multipart).contains("name=\"image[]\"; filename=\"ref1.png\"",
                "name=\"image[]\"; filename=\"ref2.jpg\"", "Content-Type: image/png", "Content-Type: image/jpeg",
                "2448x3264");
        assertThat(multipart.indexOf("ref1.png")).isLessThan(multipart.indexOf("ref2.jpg"));
        assertThat(multipart.indexOf(new String(first, StandardCharsets.ISO_8859_1)))
                .isLessThan(multipart.indexOf(new String(second, StandardCharsets.ISO_8859_1)));
    }

    @Test
    void propagatesRetryableStatusWithoutEchoingProviderSecrets() {
        assertThatThrownBy(() -> client.textToImage(base + "/failure", "local-test-key", "model", "white cup",
                "1K", "1:1", "auto")).isInstanceOf(RecoverableApiException.class)
                .hasMessageNotContaining("private-channel-secret").hasMessageNotContaining("local-test-key");
    }

    @Test
    void rejectsInvalidInputBeforeHttp() {
        assertThatThrownBy(() -> client.imageToImage(base, "key", "model", "edit", "1K", "1:1", "auto", List.of()))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> client.textToImage(base, "key", "model", "draw", "1K", "4:1", "auto"))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> client.textToImage(base, "key", "model", "draw", "1K", "1:1", "unsupported"))
                .isInstanceOf(ServiceException.class);
        assertThat(request.get()).isNull();
    }

    @Test
    void allSupportedRatiosMeetPixelAndAlignmentConstraintsExactly() {
        for (String resolution : List.of("1K", "2K", "4K")) {
            for (String ratio : List.of("1:1", "16:9", "9:16", "3:2", "2:3", "4:3", "3:4")) {
                String[] size = LuxaiImageSize.of(resolution, ratio).split("x");
                String[] requested = ratio.split(":");
                int width = Integer.parseInt(size[0]), height = Integer.parseInt(size[1]);
                assertThat(width * Integer.parseInt(requested[1])).isEqualTo(height * Integer.parseInt(requested[0]));
                assertThat(width % 16).isZero();
                assertThat(height % 16).isZero();
                assertThat(Math.max(width, height)).isLessThanOrEqualTo(3840);
                assertThat((long) width * height).isBetween(655_360L, 8_294_400L);
            }
        }
    }
}
