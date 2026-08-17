package com.mms.data.recon.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mms.data.recon.config.LlmProperties;
import jakarta.inject.Singleton;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Singleton
public class OpenAiCompatibleLlmClient {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public String complete(LlmProperties settings, String system, String user) {
        if (settings == null || !settings.configured()) {
            throw new IllegalStateException("LLM is not configured: set mms.recon.llm.url and mms.recon.llm.api-key (or pass url and apiKey on the request)");
        }
        String endpoint = LlmProperties.chatCompletionsUrl(settings.getUrl());
        String body = """
                {"model":%s,"temperature":0.2,"messages":[{"role":"system","content":%s},{"role":"user","content":%s}]}
                """.formatted(quote(settings.getModel()), quote(system), quote(user));

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(settings.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + settings.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "LLM request failed (" + response.statusCode() + "): " + truncate(response.body())
                );
            }
            return extractContent(response.body());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to call LLM at " + endpoint + ": " + e.getMessage(), e);
        }
    }

    String extractContent(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
                throw new IllegalStateException("LLM response did not include choices[0].message.content");
            }
            return content.asText();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse LLM response: " + e.getMessage(), e);
        }
    }

    private static String quote(String value) {
        String raw = value == null ? "" : value;
        return "\"" + raw
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 400 ? value : value.substring(0, 400);
    }
}
