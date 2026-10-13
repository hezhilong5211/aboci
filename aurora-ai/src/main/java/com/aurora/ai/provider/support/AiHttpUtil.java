package com.aurora.ai.provider.support;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * AI 接口通用 HTTP 工具
 *
 * <p>基于 JDK 内置 {@link java.net.http.HttpClient} + Jackson，供云雾 / GPTS / 可灵等多个
 * client 复用。类名特意叫 AiHttpUtil（而非 HttpUtil），避免与 Hutool 的 HttpUtil 冲突。</p>
 *
 * <p>方法签名稳定，作为下游 client 的公共依赖契约，请勿随意改动。</p>
 */
public class AiHttpUtil {

    /** 共享的 HttpClient，连接超时 30 秒 */
    public static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /** 共享的 Jackson 解析器 */
    public static final ObjectMapper M = new ObjectMapper();

    private AiHttpUtil() {
    }

    /**
     * 按状态码抛异常:408/429/5xx 视为可恢复(供上层退避重试),其余为普通失败(不重试)。
     * 仅在非 2xx 时调用。
     */
    private static RuntimeException httpError(int statusCode, String body) {
        String msg = "HTTP " + statusCode + ": " + truncate(body, 300);
        if (statusCode == 408 || statusCode == 429 || statusCode >= 500) {
            return new RecoverableApiException(statusCode, msg);
        }
        return new RuntimeException(msg);
    }

    /**
     * POST JSON，携带 Bearer 鉴权，返回响应体字符串。
     *
     * @param url        请求地址
     * @param json       JSON 请求体
     * @param bearer     Bearer Token（自动拼接 "Bearer " 前缀）
     * @param timeoutSec 请求超时（秒）
     * @return 响应体字符串
     * @throws Exception 网络异常；非 2xx 状态码抛 RuntimeException（带截断响应体）
     */
    public static String postJson(String url, String json, String bearer, int timeoutSec) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearer)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> resp = CLIENT.send(req, BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw httpError(resp.statusCode(), resp.body());
        }
        return resp.body();
    }

    /**
     * POST JSON，携带 Bearer 鉴权，返回原始响应字节。
     *
     * <p>适用于云雾等可能返回超大文本或二进制内容的场景，避免按字符串读取造成的开销或损坏。</p>
     *
     * @param url        请求地址
     * @param json       JSON 请求体
     * @param bearer     Bearer Token（自动拼接 "Bearer " 前缀）
     * @param timeoutSec 请求超时（秒）
     * @return 原始响应字节
     * @throws Exception 网络异常；非 2xx 状态码抛 RuntimeException（带截断响应体）
     */
    public static byte[] postJsonRaw(String url, String json, String bearer, int timeoutSec) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearer)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<byte[]> resp = CLIENT.send(req, BodyHandlers.ofByteArray());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            // 错误时按字符串解析响应体便于排查，限制长度避免日志膨胀
            String body = resp.body() == null ? "" : new String(resp.body(), java.nio.charset.StandardCharsets.UTF_8);
            throw httpError(resp.statusCode(), body);
        }
        return resp.body();
    }

    /**
     * POST multipart/form-data，携带 Bearer 鉴权，返回原始响应字节。
     *
     * <p>用于图生图（image-edit，如云雾 {@code /v1/images/edits}）等需「文件 + 文本字段」混合提交的端点。
     * 手工拼 multipart body(JDK HttpClient 无内置 multipart 构造器):每个分项以 {@code --boundary} 分隔,
     * 文件分项带 filename + Content-Type,末尾 {@code --boundary--} 收尾。</p>
     *
     * @param url        请求地址
     * @param bearer     Bearer Token（自动拼接 "Bearer " 前缀）
     * @param timeoutSec 请求超时（秒）
     * @param fields     普通文本字段(name→value,按插入序写入);可空
     * @param files      文件分项(每项一个 part);可空
     * @return 原始响应字节
     * @throws Exception 网络异常;非 2xx 抛 RuntimeException(带截断响应体)
     */
    public static byte[] postMultipartRaw(String url, String bearer, int timeoutSec,
                                          Map<String, String> fields, List<FilePart> files) throws Exception {
        String boundary = "----AuroraBoundary" + Long.toHexString(System.nanoTime());
        byte[] dash = "--".getBytes(StandardCharsets.UTF_8);
        byte[] crlf = "\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] bnd = boundary.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        if (fields != null) {
            for (Map.Entry<String, String> e : fields.entrySet()) {
                out.write(dash);
                out.write(bnd);
                out.write(crlf);
                out.write(("Content-Disposition: form-data; name=\"" + e.getKey() + "\"")
                        .getBytes(StandardCharsets.UTF_8));
                out.write(crlf);
                out.write(crlf);
                out.write(e.getValue() == null ? new byte[0] : e.getValue().getBytes(StandardCharsets.UTF_8));
                out.write(crlf);
            }
        }
        if (files != null) {
            for (FilePart f : files) {
                out.write(dash);
                out.write(bnd);
                out.write(crlf);
                out.write(("Content-Disposition: form-data; name=\"" + f.fieldName
                        + "\"; filename=\"" + f.filename + "\"").getBytes(StandardCharsets.UTF_8));
                out.write(crlf);
                out.write(("Content-Type: " + (f.contentType == null ? "application/octet-stream" : f.contentType))
                        .getBytes(StandardCharsets.UTF_8));
                out.write(crlf);
                out.write(crlf);
                out.write(f.data);
                out.write(crlf);
            }
        }
        out.write(dash);
        out.write(bnd);
        out.write(dash);
        out.write(crlf);
        byte[] body = out.toByteArray();

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + bearer)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<byte[]> resp = CLIENT.send(req, BodyHandlers.ofByteArray());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            String b = resp.body() == null ? "" : new String(resp.body(), StandardCharsets.UTF_8);
            throw httpError(resp.statusCode(), b);
        }
        return resp.body();
    }

    /**
     * multipart 文件分项(字段名 + 文件名 + Content-Type + 字节)。
     */
    public static class FilePart {
        public final String fieldName;
        public final String filename;
        public final String contentType;
        public final byte[] data;

        public FilePart(String fieldName, String filename, String contentType, byte[] data) {
            this.fieldName = fieldName;
            this.filename = filename;
            this.contentType = contentType;
            this.data = data;
        }
    }

    /**
     * GET，携带 Bearer 鉴权，返回响应体字符串。
     *
     * @param url        请求地址
     * @param bearer     Bearer Token（自动拼接 "Bearer " 前缀）
     * @param timeoutSec 请求超时（秒）
     * @return 响应体字符串
     * @throws Exception 网络异常
     */
    public static String get(String url, String bearer, int timeoutSec) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Authorization", "Bearer " + bearer)
                .GET()
                .build();
        HttpResponse<String> resp = CLIENT.send(req, BodyHandlers.ofString());
        return resp.body();
    }

    /**
     * 下载二进制资源（图片 / 视频等），无鉴权。
     *
     * @param url 资源地址
     * @return 资源字节
     * @throws Exception 网络异常；非 2xx 状态码抛 RuntimeException
     */
    public static byte[] download(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> resp = CLIENT.send(req, BodyHandlers.ofByteArray());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("下载失败 HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    /**
     * 截断字符串，用于日志 / 异常信息，避免输出过长内容。
     *
     * @param s 原字符串（可为 null）
     * @param n 最大保留长度
     * @return 截断后的字符串（null 转空串）
     */
    public static String truncate(String s, int n) {
        return s == null ? "" : (s.length() <= n ? s : s.substring(0, n));
    }
}
