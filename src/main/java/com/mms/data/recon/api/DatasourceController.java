package com.mms.data.recon.api;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.mms.data.recon.config.DatasourceCatalog;

import java.util.Comparator;
import java.util.List;

@Tag(name = "Datasources")
@Controller("/api/datasources")
public class DatasourceController {

    private final DatasourceCatalog catalog;

    public DatasourceController(DatasourceCatalog catalog) {
        this.catalog = catalog;
    }

    @Get
    public List<DatasourceApiModel> list() {
        return catalog.asMap().entrySet().stream()
                .map(entry -> new DatasourceApiModel(entry.getKey(), entry.getValue().name()))
                .sorted(Comparator.comparing(DatasourceApiModel::name))
                .toList();
    }
}
