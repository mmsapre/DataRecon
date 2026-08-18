package com.mms.data.recon.api;

import com.mms.data.recon.config.DatasourceCatalog;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@Tag(name = "Datasources")
@RestController
@RequestMapping("/api/datasources")
public class DatasourceController {

    private final DatasourceCatalog catalog;

    public DatasourceController(DatasourceCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<DatasourceApiModel> list() {
        return catalog.asMap().entrySet().stream()
                .map(entry -> new DatasourceApiModel(entry.getKey(), entry.getValue().name()))
                .sorted(Comparator.comparing(DatasourceApiModel::name))
                .toList();
    }
}
