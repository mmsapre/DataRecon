package com.mms.data.recon.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class LlmSummaryRequest {

    private String url;

    @JsonProperty("apiKey")
    @JsonAlias({"api-key", "api_key"})
    private String apiKey;

    private String model;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}
