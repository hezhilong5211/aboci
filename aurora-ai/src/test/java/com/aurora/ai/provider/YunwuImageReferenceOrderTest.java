package com.aurora.ai.provider;

import com.aurora.common.core.exception.ServiceException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YunwuImageReferenceOrderTest {

    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<byte[]> body = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/images/edits", exchange -> {
            requests.incrementAndGet();
            body.set(exchange.getRequestBody().readAllBytes());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] response = ("{\"data\":[{\"b64_json\":\""
                    + Base64.getEncoder().encodeToString(png(9)) + "\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void shouldUploadActualBytesInCompiledOrderNotJustNumberFilenames() {
        List<byte[]> references = List.of(png(22), png(11), png(3), png(1));

        generate(references);

        List<byte[]> actual = uploadedFiles();
        assertThat(actual).hasSize(4);
        for (int i = 0; i < references.size(); i++) assertThat(actual.get(i)).isEqualTo(references.get(i));
        assertThat(requests).hasValue(1);
    }

    @Test
    void shouldKeepSingleRetryReferenceAsTheOnlyFile() {
        byte[] anchor = png(30);

        generate(List.of(anchor));

        assertThat(uploadedFiles()).hasSize(1);
        assertThat(uploadedFiles().get(0)).isEqualTo(anchor);
        assertThat(new String(body.get(), StandardCharsets.ISO_8859_1))
                .contains("name=\"image\"; filename=\"ref1.png\"")
                .doesNotContain("name=\"image[]\"");
    }

    @Test
    void shouldRejectMissingOrInvalidMiddleInputWithoutSendingPartialMultipart() {
        for (byte[] invalid : Arrays.asList(null, new byte[0], "not-an-image".getBytes(StandardCharsets.UTF_8))) {
            assertThatThrownBy(() -> generate(Arrays.asList(png(1), invalid, png(3))))
                    .isInstanceOf(ServiceException.class).hasMessageContaining("第2张参考图");
        }
        assertThat(requests).hasValue(0);
    }

    private void generate(List<byte[]> references) {
        new YunwuImageClient().imageToImage(baseUrl, "test-key", "gpt-image-2", "one image", "1K", "1:1", references);
    }

    private List<byte[]> uploadedFiles() {
        String boundary = contentType.get().split("boundary=", 2)[1];
        String multipart = new String(body.get(), StandardCharsets.ISO_8859_1);
        List<byte[]> files = new ArrayList<>();
        for (String part : multipart.split(Pattern.quote("--" + boundary))) {
            int headerEnd = part.indexOf("\r\n\r\n");
            if (headerEnd >= 0 && part.substring(0, headerEnd).contains("filename=")) {
                String payload = part.substring(headerEnd + 4);
                if (payload.endsWith("\r\n")) payload = payload.substring(0, payload.length() - 2);
                files.add(payload.getBytes(StandardCharsets.ISO_8859_1));
            }
        }
        return files;
    }

    private static byte[] png(int marker) {
        return new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, (byte) marker};
    }
}
