package com.mms.data.recon.api;

import com.mms.data.recon.config.LlmProperties;
import com.mms.data.recon.llm.LlmSummaryService;
import com.mms.data.recon.recrun.RecRunService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "LLM summaries")
@RestController
@RequestMapping("/api")
public class LlmSummaryController {

    private final LlmSummaryService summaries;
    private final RecRunService runs;

    public LlmSummaryController(LlmSummaryService summaries, RecRunService runs) {
        this.summaries = summaries;
        this.runs = runs;
    }

    @GetMapping("/runs/{runId}/summary")
    public ResponseEntity<?> summarizeRunGet(@PathVariable long runId) {
        return summarizeRun(runId, null);
    }

    @PostMapping("/runs/{runId}/summary")
    public ResponseEntity<?> summarizeRunPost(
            @PathVariable long runId,
            @Nullable @RequestBody LlmSummaryRequest request) {
        return summarizeRun(runId, request);
    }

    @GetMapping("/domains/{domainId}/runs/{domainRunId}/summary")
    public ResponseEntity<?> summarizeDomainGet(
            @PathVariable String domainId,
            @PathVariable long domainRunId) {
        return summarizeDomain(domainId, domainRunId, null);
    }

    @PostMapping("/domains/{domainId}/runs/{domainRunId}/summary")
    public ResponseEntity<?> summarizeDomainPost(
            @PathVariable String domainId,
            @PathVariable long domainRunId,
            @Nullable @RequestBody LlmSummaryRequest request) {
        return summarizeDomain(domainId, domainRunId, request);
    }

    private ResponseEntity<?> summarizeRun(long runId, LlmSummaryRequest request) {
        try {
            LlmSummaryService.Summary summary = summaries.summarizeRun(runId, from(request));
            return ResponseEntity.ok(new LlmSummaryApiModel(summary.runId(), summary.model(), summary.text()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            if (notConfigured(e)) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> summarizeDomain(String domainId, long domainRunId, LlmSummaryRequest request) {
        try {
            runs.domainRun(domainId, domainRunId);
            LlmSummaryService.Summary summary = summaries.summarizeDomainRun(domainRunId, from(request));
            return ResponseEntity.ok(new LlmSummaryApiModel(summary.runId(), summary.model(), summary.text()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            if (notConfigured(e)) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
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
