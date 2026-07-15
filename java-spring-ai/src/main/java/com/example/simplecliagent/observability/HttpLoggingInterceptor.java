package com.example.simplecliagent.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RestClient/RestTemplate 拦截器：捕获 OpenAI 兼容 HTTP 往返并写入 JSONL。
 *
 * <p>对齐 Python httpx event hooks。响应 body 会被缓存，以便下游仍可读。
 */
public class HttpLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingInterceptor.class);

    private final HttpJsonlLogger jsonlLogger;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpLoggingInterceptor(HttpJsonlLogger jsonlLogger) {
        this.jsonlLogger = jsonlLogger;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        Map<String, Object> reqMap = new LinkedHashMap<>();
        reqMap.put("method", request.getMethod() == null ? null : request.getMethod().name());
        reqMap.put("url", request.getURI().toString());
        reqMap.put("headers", toHeaderMap(request.getHeaders()));
        reqMap.put("body", tryParseBody(body));

        ClientHttpResponse response = null;
        String error = null;
        byte[] responseBody = new byte[0];
        int status = -1;
        Map<String, String> respHeaders = Map.of();

        try {
            // 诊断：若 URI 无 scheme，在真正发送前打出，便于对照 base-url 配置
            if (request.getURI().getScheme() == null) {
                log.error("HTTP request URI has no scheme: {} (check spring.ai.openai.base-url)",
                        request.getURI());
            }
            response = execution.execute(request, body);
            status = response.getStatusCode().value();
            respHeaders = toHeaderMap(response.getHeaders());
            // 读完 body 后包装可重复读的响应，避免 ChatModel 再读失败
            responseBody = StreamUtils.copyToByteArray(response.getBody());
            return new BufferingClientHttpResponseWrapper(response, responseBody);
        } catch (IOException ex) {
            error = ex.getMessage();
            throw ex;
        } finally {
            Map<String, Object> respMap = new LinkedHashMap<>();
            respMap.put("status_code", status);
            respMap.put("headers", respHeaders);
            respMap.put("body", tryParseBody(responseBody));
            jsonlLogger.writeExchange(reqMap, respMap, error);
            log.debug("http wire logged {} {} status={}",
                    reqMap.get("method"), reqMap.get("url"), status);
        }
    }

    private Map<String, String> toHeaderMap(HttpHeaders headers) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            // 多值用逗号拼接，与常见 curl -v 观感接近
            map.put(e.getKey(), String.join(", ", e.getValue()));
        }
        return map;
    }

    private Object tryParseBody(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return null;
        }
        String text = new String(raw, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * 缓存 body 的响应包装，保证拦截器读过后客户端仍能读取。
     */
    static final class BufferingClientHttpResponseWrapper implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private final byte[] body;

        BufferingClientHttpResponseWrapper(ClientHttpResponse delegate, byte[] body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
