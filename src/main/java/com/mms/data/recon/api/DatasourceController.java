package com.mms.data.recon.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Step 1 of setup: register named source/target datasources (optionally tagged),
 * then create domains and profiles that attach those names.
 */
@Tag(name = "Datasources")
@RestController
@RequestMapping("/api/datasources")
public class DatasourceController {

    private final DatasourceRegistry registry;

    public DatasourceController(DatasourceRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    @Operation(summary = "List datasources", description = "Optional tag filter groups related connections")
    public List<DatasourceApiModel> list(@RequestParam(required = false) String tag) {
        return registry.list(tag);
    }

    @GetMapping("/{name}")
    public DatasourceApiModel get(@PathVariable String name) {
        return registry.get(name);
    }

    @PostMapping
    @Operation(summary = "Create datasource", description = "Register a postgres|mongo|bigquery|file connection before profiling")
    public ResponseEntity<DatasourceApiModel> create(@RequestBody DatasourceUpsertRequest request) {
        DatasourceApiModel created = registry.create(request);
        return ResponseEntity.created(URI.create("/api/datasources/" + created.name())).body(created);
    }

    @PutMapping("/{name}")
    public DatasourceApiModel update(@PathVariable String name, @RequestBody DatasourceUpsertRequest request) {
        return registry.update(name, request);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        registry.delete(name);
        return ResponseEntity.noContent().build();
    }
}
