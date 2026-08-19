package com.mms.data.recon.api;

import java.time.Instant;
import java.util.List;

public record DatasourceApiModel(
        String name,
        String type,
        String schema,
        List<String> tags,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        boolean active,
        int version) {

    public DatasourceApiModel(String name, String type) {
        this(name, type, null, List.of(), null, null, null, null, true, 1);
    }

    public DatasourceApiModel(String name, String type, List<String> tags) {
        this(name, type, null, tags, null, null, null, null, true, 1);
    }
}
