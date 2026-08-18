package com.mms.data.recon.api;

import java.time.Instant;

public record RunApiModel(
        long id,
        String datasetId,
        String domainId,
        String profileId,
        Long domainRunId,
        String status,
        Instant startedAt,
        Instant completedAt,
        long sourceCount,
        long targetCount,
        long matchedCount,
        long mismatchedCount,
        long sourceOnlyCount,
        long targetOnlyCount,
        String errorMessage,
        boolean active,
        String reconMode,
        String sourceQuery,
        String targetQuery,
        java.util.List<String> conditionFields,
        String runScope,
        Long baselineRunId) {}
