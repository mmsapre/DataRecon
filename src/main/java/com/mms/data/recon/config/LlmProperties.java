package com.mms.data.recon.config;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties(MmsRecon.PREFIX + ".llm")
public class LlmProperties {

    private String url = "";
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private int timeoutSeconds = 30;
    private int maxRecords = 50;

    public boolean configured() {
        return !blank(url) && !blank(apiKey);
    }

    public LlmProperties overlay(String urlOverride, String apiKeyOverride, String modelOverride) {
        LlmProperties copy = new LlmProperties();
        copy.setUrl(!blank(urlOverride) ? urlOverride : url);
        copy.setApiKey(!blank(apiKeyOverride) ? apiKeyOverride : apiKey);
        copy.setModel(!blank(modelOverride) ? modelOverride : model);
        copy.setTimeoutSeconds(timeoutSeconds);
        copy.setMaxRecords(maxRecords);
        return copy;
    }

    public static String chatCompletionsUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("LLM url is required");
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String lower = trimmed.toLowerCase();
        if (lower.endsWith("/chat/completions") || lower.endsWith("/completions")) {
            return trimmed;
        }
        if (lower.endsWith("/v1")) {
            return trimmed + "/chat/completions";
        }
        return trimmed + "/v1/chat/completions";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url == null ? "" : url; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model == null || model.isBlank() ? "gpt-4o-mini" : model; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = Math.max(1, timeoutSeconds); }

    public int getMaxRecords() { return maxRecords; }
    public void setMaxRecords(int maxRecords) { this.maxRecords = Math.max(1, maxRecords); }
}
