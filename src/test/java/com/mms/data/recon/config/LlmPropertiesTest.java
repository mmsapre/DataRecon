package com.mms.data.recon.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmPropertiesTest {

    @Test
    void configuredOnlyWhenUrlAndApiKeyAreSet() {
        LlmProperties properties = new LlmProperties();
        assertFalse(properties.configured());
        properties.setUrl("https://api.openai.com/v1");
        assertFalse(properties.configured());
        properties.setApiKey("sk-test");
        assertTrue(properties.configured());
    }

    @Test
    void buildsChatCompletionsUrl() {
        assertEquals(
                "https://api.openai.com/v1/chat/completions",
                LlmProperties.chatCompletionsUrl("https://api.openai.com/v1")
        );
        assertEquals(
                "http://localhost:11434/v1/chat/completions",
                LlmProperties.chatCompletionsUrl("http://localhost:11434")
        );
        assertEquals(
                "https://example.com/v1/chat/completions",
                LlmProperties.chatCompletionsUrl("https://example.com/v1/chat/completions")
        );
    }

    @Test
    void overlayPrefersRequestUrlAndKey() {
        LlmProperties configured = new LlmProperties();
        configured.setUrl("https://config.example/v1");
        configured.setApiKey("config-key");
        configured.setModel("config-model");

        LlmProperties merged = configured.overlay("https://override.example/v1", "override-key", null);
        assertEquals("https://override.example/v1", merged.getUrl());
        assertEquals("override-key", merged.getApiKey());
        assertEquals("config-model", merged.getModel());
    }
}
