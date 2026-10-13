package com.aurora.framework.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.web.util.HtmlUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * XSS JSON 请求体包装器
 * 对 JSON 请求体中的字符串进行 XSS 过滤
 *
 * @author aurora
 */
public class XssJsonRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public XssJsonRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);

        // 读取原始请求体
        String originalBody = readBody(request);

        // 过滤 XSS
        String filteredBody = filterXssInJson(originalBody);

        this.body = filteredBody.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 读取请求体
     */
    private String readBody(HttpServletRequest request) throws IOException {
        // 读取原始字节流,避免 readLine 逐行拼接丢失换行(破坏 JSON 字符串值内换行 / 非 JSON 原文)
        try (InputStream in = request.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 过滤 JSON 中的 XSS
     */
    private String filterXssInJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode filteredNode = filterNode(node);
            return objectMapper.writeValueAsString(filteredNode);
        } catch (JsonProcessingException e) {
            // 如果不是有效 JSON，直接返回原文（进行 HTML 转义）
            return HtmlUtils.htmlEscape(json);
        }
    }

    /**
     * 递归过滤 JSON 节点
     */
    private JsonNode filterNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }

        if (node.isTextual()) {
            String text = node.asText();
            // 对文本进行 XSS 过滤
            return new TextNode(cleanXss(text));
        }

        if (node.isObject()) {
            ObjectNode objectNode = objectMapper.createObjectNode();
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                objectNode.set(fieldName, filterNode(node.get(fieldName)));
            }
            return objectNode;
        }

        if (node.isArray()) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (JsonNode element : node) {
                arrayNode.add(filterNode(element));
            }
            return arrayNode;
        }

        // 其他类型（数字、布尔等）直接返回
        return node;
    }

    /**
     * 清理 XSS 攻击代码
     */
    private String cleanXss(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        // HTML 转义
        return HtmlUtils.htmlEscape(value);
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // 不需要实现
            }

            @Override
            public int read() {
                return byteArrayInputStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8));
    }
}
