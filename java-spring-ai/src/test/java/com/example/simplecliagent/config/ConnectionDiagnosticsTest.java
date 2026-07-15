package com.example.simplecliagent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionDiagnosticsTest {

    @Test
    void acceptsNormalPair() {
        assertDoesNotThrow(() ->
                ConnectionDiagnostics.validate("https://api.openai.com", "sk-abc12345"));
    }

    @Test
    void rejectsBaseUrlThatLooksLikeKey() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                ConnectionDiagnostics.validate("tp-cf2h39cjsgf6smrwpy84", "https://api.openai.com"));
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
