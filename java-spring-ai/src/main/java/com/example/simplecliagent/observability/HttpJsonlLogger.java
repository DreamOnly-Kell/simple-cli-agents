package com.example.simplecliagent.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将 HTTP 请求/响应追加为 JSONL，对齐 Python {@code HttpJsonlLogger}。
 *
 * <p>一行 = 一次 exchange；Authorization 等头脱敏。
 */
public class HttpJsonlLogger {

    private static final Logger log = LoggerFactory.getLogger(HttpJsonlLogger.class);

    private static final Set<String> SENSITIVE = Set.of(
            "authorization", "api-key", "x-api-key", "proxy-authorization");

    private final Path path;
    private final ObjectMapper mapper;

    /**
     * @param path 例如 logs/http-session-20260712-153000.jsonl
     */
    public HttpJsonlLogger(Path path) {
        this.path = path;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create http log dir: " + path.getParent(), e);
        }
    }

    public Path getPath() {
        return path;
    }

    /**
     * 追加一条 exchange 记录。
     *
     * @param request  含 method/url/headers/body
     * @param response 含 statusCode/headers/body，可为 null
     * @param error    可选错误信息
     */
    public synchronized void writeExchange(
            Map<String, Object> request,
            Map<String, Object> response,
            String error) {
        try {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("ts", Instant.now().toString());
            record.put("direction", "exchange");
            record.put("request", sanitizeRequest(request));
            if (response != null) {
                record.put("response", sanitizeResponse(response));
            }
            if (error != null) {
                record.put("error", error);
            }
            String line = mapper.writeValueAsString(record) + "\n";
            Files.writeString(path, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 日志失败不应打断 agent
            log.warn("failed to write http jsonl: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeRequest(Map<String, Object> request) {
        Map<String, Object> copy = new LinkedHashMap<>(request);
        Object headers = copy.get("headers");
        if (headers instanceof Map<?, ?> h) {
            copy.put("headers", redactHeaders((Map<String, ?>) h));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeResponse(Map<String, Object> response) {
        Map<String, Object> copy = new LinkedHashMap<>(response);
        Object headers = copy.get("headers");
        if (headers instanceof Map<?, ?> h) {
            copy.put("headers", redactHeaders((Map<String, ?>) h));
        }
        return copy;
    }

    /**
     * 脱敏敏感头；Bearer 保留前缀。
     */
    public static Map<String, String> redactHeaders(Map<String, ?> headers) {
        Map<String, String> out = new LinkedHashMap<>();
        if (headers == null) {
            return out;
        }
        for (Map.Entry<String, ?> e : headers.entrySet()) {
            String key = String.valueOf(e.getKey());
            String value = e.getValue() == null ? "" : String.valueOf(e.getValue());
            String lk = key.toLowerCase(Locale.ROOT);
            if (SENSITIVE.contains(lk)) {
                if ("authorization".equals(lk) && value.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
                    out.put(key, "Bearer ***");
                } else {
                    out.put(key, "***");
                }
            } else {
                out.put(key, value);
            }
        }
        return out;
    }
}
