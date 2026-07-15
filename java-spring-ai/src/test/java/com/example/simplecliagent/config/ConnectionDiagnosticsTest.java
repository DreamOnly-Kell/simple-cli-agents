package com.example.simplecliagent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionDiagnosticsTest {

    @Test
    void acceptsNormalPair() {
        assertDoesNotThrow(() ->
                ConnectionDiagnostics.validate("https://api.openai.com", "tp-test-not-a-real-key-000"));
    }

    @Test
    void rejectsBaseUrlThatLooksLikeKey() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                // synthetic "looks like key, not URL" — must not be a real secret
                ConnectionDiagnostics.validate("tp-test-not-a-real-key-000", "https://api.openai.com"));
        assertTrue(ex.getMessage().toLowerCase().contains("base-url")
                || ex.getMessage().toLowerCase().contains("swapped"));
    }

    @Test
    void rejectsApiKeyThatLooksLikeUrl() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                ConnectionDiagnostics.validate("https://api.openai.com", "https://token.example.com"));
        assertTrue(ex.getMessage().toLowerCase().contains("api-key")
                || ex.getMessage().toLowerCase().contains("swapped"));
    }
}
