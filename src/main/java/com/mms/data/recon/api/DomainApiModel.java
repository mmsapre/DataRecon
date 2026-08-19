package com.mms.data.recon.api;

import java.time.Instant;
import java.util.List;

public record DomainApiModel(
        String id,
        String hashingStrategy,
        List<String> tags,
        String sourceDatasource,
        String targetDatasource,
        List<ProfileApiModel> profiles,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        boolean active,
        int version) {}
