package com.mms.data.recon.api;

import java.util.List;

public record DatasourceApiModel(String name, String type, List<String> tags) {
    public DatasourceApiModel(String name, String type) {
        this(name, type, List.of());
    }
}
