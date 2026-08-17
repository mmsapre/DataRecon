package com.mms.data.recon.api;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.mms.data.recon.config.LlmProperties;
import com.mms.data.recon.llm.LlmSummaryService;
import com.mms.data.recon.recrun.RecRunService;

import java.util.Map;

@Tag(name = "LLM summaries")
@Controller("/api")
public class LlmSummaryController {

    private final LlmSummaryService summaries;
    private final RecRunService runs;

    public LlmSummaryController(LlmSummaryService summaries, RecRunService runs) {
        this.summaries = summaries;
        this.runs = runs;
    }

    @Get("/runs/{runId}/summary")
    public HttpResponse<?> summarizeRunGet(@PathVariable long runId) {
        return summarizeRun(runId, null);
    }

    @Post("/runs/{runId}/summary")
    public HttpResponse<?> summarizeRunPost(
            @PathVariable long runId,
            @Nullable @Body LlmSummaryRequest request) {
        return summarizeRun(runId, request);
    }

    @Get("/domains/{domainId}/runs/{domainRunId}/summary")
    public HttpResponse<?> summarizeDomainGet(
            @PathVariable String domainId,
            @PathVariable long domainRunId) {
        return summarizeDomain(domainId, domainRunId, null);
    }

    @Post("/domains/{domainId}/runs/{domainRunId}/summary")
    public HttpResponse<?> summarizeDomainPost(
            @PathVariable String domainId,
            @PathVariable long domainRunId,
            @Nullable @Body LlmSummaryRequest request) {
        return summarizeDomain(domainId, domainRunId, request);
    }

    private HttpResponse<?> summarizeRun(long runId, LlmSummaryRequest request) {
        try {
            LlmSummaryService.Summary summary = summaries.summarizeRun(runId, from(request));
            return HttpResponse.ok(new LlmSummaryApiModel(summary.runId(), summary.model(), summary.text()));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            if (notConfigured(e)) {
                return HttpResponse.badRequest(Map.of("error", e.getMessage()));
            }
            return HttpResponse.serverError(Map.of("error", e.getMessage()));
        }
    }

    private HttpResponse<?> summarizeDomain(String domainId, long domainRunId, LlmSummaryRequest request) {
        try {
            runs.domainRun(domainId, domainRunId);
            LlmSummaryService.Summary summary = summaries.summarizeDomainRun(domainRunId, from(request));
            return HttpResponse.ok(new LlmSummaryApiModel(summary.runId(), summary.model(), summary.text()));
        } catch (IllegalArgumentException e) {
            return HttpResponse.badRequest(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            if (notConfigured(e)) {
                return HttpResponse.badRequest(Map.of("error", e.getMessage()));
            }
            return HttpResponse.serverError(Map.of("error", e.getMessage()));
        }
    }

    private static LlmProperties from(LlmSummaryRequest request) {
        if (request == null) {
            return null;
        }
        LlmProperties properties = new LlmProperties();
        properties.setUrl(request.getUrl());
        properties.setApiKey(request.getApiKey());
        properties.setModel(request.getModel());
        return properties;
    }

    private static boolean notConfigured(IllegalStateException error) {
        return error.getMessage() != null && error.getMessage().contains("LLM is not configured");
    }
}
