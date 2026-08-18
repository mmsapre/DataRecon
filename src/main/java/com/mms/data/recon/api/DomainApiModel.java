package com.mms.data.recon.api;

import java.time.Instant;
import java.util.List;

public record DomainApiModel(
        String id,
        String schedule,
        String hashingStrategy,
        List<String> tags,
        List<ProfileApiModel> profiles,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        boolean active,
        int version) {}
