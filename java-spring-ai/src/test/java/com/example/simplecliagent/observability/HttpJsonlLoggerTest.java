package com.example.simplecliagent.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSONL 脱敏与落盘，对齐 Python tests/test_http_logging.py。
 */
class HttpJsonlLoggerTest {

    @TempDir
    Path tempDir;

    @Test
    void redactAuthorizationHeader() {
        Map<String, String> headers = Map.of(
                "Authorization", "Bearer sk-secret-key-123",
                "Content-Type", "application/json",
                "api-key", "also-secret"
        );
        Map<String, String> redacted = HttpJsonlLogger.redactHeaders(headers);
        assertEquals("Bearer ***", redacted.get("Authorization"));
        assertEquals("***", redacted.get("api-key"));
        assertEquals("application/json", redacted.get("Content-Type"));
        assertFalse(redacted.toString().contains("sk-secret"));
    }

    @Test
    void jsonlWriterAppendsValidLine() throws Exception {
        Path logPath = tempDir.resolve("session.jsonl");
        HttpJsonlLogger logger = new HttpJsonlLogger(logPath);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", "POST");
        request.put("url", "https://api.example.com/v1/chat/completions");
        request.put("headers", Map.of(
                "Authorization", "Bearer sk-abc",
                "Content-Type", "application/json"));
        request.put("body", Map.of("model", "gpt-test", "messages", java.util.List.of()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status_code", 200);
        response.put("headers", Map.of("content-type", "application/json"));
        response.put("body", Map.of("choices", java.util.List.of()));

        logger.writeExchange(request, response, null);

        String text = Files.readString(logPath);
        assertFalse(text.contains("sk-abc"));
        JsonNode record = new ObjectMapper().readTree(text.trim());
        assertEquals("Bearer ***", record.path("request").path("headers").path("Authorization").asText());
        assertEquals(200, record.path("response").path("status_code").asInt());
        assertEquals("gpt-test", record.path("request").path("body").path("model").asText());
        assertTrue(record.path("ts").asText().length() > 0);
    }
}
