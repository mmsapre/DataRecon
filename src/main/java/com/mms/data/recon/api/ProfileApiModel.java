package com.mms.data.recon.api;

public record ProfileApiModel(
        String domainId,
        String profileId,
        String id,
        String sourceDatasource,
        String sourceType,
        String targetDatasource,
        String targetType,
        String migrationKeyType,
        java.util.List<String> migrationKeyColumns,
        String hashingStrategy,
        String schedule,
        String reconMode,
        java.util.List<String> conditionFields,
        java.util.List<String> tags) {}
