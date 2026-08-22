package com.mms.data.recon.api;

import java.time.Instant;
import java.util.List;

public record ProfileApiModel(
        String domainId,
        String profileId,
        String id,
        String sourceDatasource,
        String sourceType,
        String targetDatasource,
        String targetType,
        String migrationKeyType,
        List<String> migrationKeyColumns,
        List<String> identifiers,
        String hashingStrategy,
        String reconMode,
        List<String> conditionFields,
        List<String> tags,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        boolean active,
        int version) {}
