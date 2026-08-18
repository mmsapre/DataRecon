package com.mms.data.recon.api;

import com.mms.data.recon.config.DatasourceCatalog;
import com.mms.data.recon.config.RecConfiguration;
import com.mms.data.recon.dataset.DatasetConfiguration;
import com.mms.data.recon.dataset.DomainConfiguration;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@Tag(name = "Domains")
@RestController
@RequestMapping("/api")
public class DomainController {

    private final RecConfiguration configuration;
    private final DatasourceCatalog datasourceCatalog;
    private final RecCatalogService catalog;

    public DomainController(
            RecConfiguration configuration,
            DatasourceCatalog datasourceCatalog,
            RecCatalogService catalog) {
        this.configuration = configuration;
        this.datasourceCatalog = datasourceCatalog;
        this.catalog = catalog;
    }

    @GetMapping("/domains")
    public List<DomainApiModel> listDomains() {
        return configuration.getDomains().values().stream()
                .map(this::domain)
                .toList();
    }

    @PostMapping("/domains")
    public ResponseEntity<DomainApiModel> createDomain(@RequestBody DomainUpsertRequest request) {
        DomainApiModel created = domain(catalog.createDomain(request));
        return ResponseEntity.created(URI.create("/api/domains/" + created.id())).body(created);
    }

    @GetMapping("/domains/{domainId}")
    public DomainApiModel getDomain(@PathVariable String domainId) {
        return domain(configuration.requireDomain(domainId));
    }

    @PutMapping("/domains/{domainId}")
    public DomainApiModel updateDomain(@PathVariable String domainId, @RequestBody DomainUpsertRequest request) {
        return domain(catalog.updateDomain(domainId, request));
    }

    @DeleteMapping("/domains/{domainId}")
    public ResponseEntity<Void> deleteDomain(@PathVariable String domainId) {
        catalog.deleteDomain(domainId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/domains/{domainId}/profiles")
    public List<ProfileApiModel> listProfiles(@PathVariable String domainId) {
        return configuration.requireDomain(domainId).getProfiles().values().stream()
                .map(this::profile)
                .toList();
    }

    @PostMapping("/domains/{domainId}/profiles")
    public ResponseEntity<ProfileApiModel> createProfile(
            @PathVariable String domainId,
            @RequestBody ProfileUpsertRequest request) {
        ProfileApiModel created = profile(catalog.createProfile(domainId, request));
        return ResponseEntity.created(URI.create("/api/domains/" + domainId + "/profiles/" + created.profileId()))
                .body(created);
    }

    @GetMapping("/domains/{domainId}/profiles/{profileId}")
    public ProfileApiModel getProfile(@PathVariable String domainId, @PathVariable String profileId) {
        return profile(configuration.requireProfile(domainId, profileId));
    }

    @PutMapping("/domains/{domainId}/profiles/{profileId}")
    public ProfileApiModel updateProfile(
            @PathVariable String domainId,
            @PathVariable String profileId,
            @RequestBody ProfileUpsertRequest request) {
        return profile(catalog.updateProfile(domainId, profileId, request));
    }

    @DeleteMapping("/domains/{domainId}/profiles/{profileId}")
    public ResponseEntity<Void> deleteProfile(@PathVariable String domainId, @PathVariable String profileId) {
        catalog.deleteProfile(domainId, profileId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/domains/{domainId}/profiles/{profileId}/datasources")
    public ProfileApiModel attachDatasources(
            @PathVariable String domainId,
            @PathVariable String profileId,
            @RequestBody AttachDatasourcesRequest request) {
        return profile(catalog.attachDatasources(domainId, profileId, request));
    }

    @PutMapping("/domains/{domainId}/profiles/{profileId}/source")
    public ProfileApiModel updateSource(
            @PathVariable String domainId,
            @PathVariable String profileId,
            @RequestBody SideRequest request) {
        return profile(catalog.updateSource(domainId, profileId, request));
    }

    @PutMapping("/domains/{domainId}/profiles/{profileId}/target")
    public ProfileApiModel updateTarget(
            @PathVariable String domainId,
            @PathVariable String profileId,
            @RequestBody SideRequest request) {
        return profile(catalog.updateTarget(domainId, profileId, request));
    }

    private DomainApiModel domain(DomainConfiguration domain) {
        return new DomainApiModel(
                domain.getId(),
                domain.getSchedule(),
                domain.getHashingStrategy() == null ? null : domain.getHashingStrategy().name(),
                domain.getProfiles().values().stream().map(this::profile).toList()
        );
    }

    private ProfileApiModel profile(DatasetConfiguration profile) {
        var key = profile.getMigrationKey() != null
                ? profile.getMigrationKey()
                : profile.getSource() == null ? null : profile.getSource().getMigrationKey();
        return new ProfileApiModel(
                profile.getDomainId(),
                profile.getProfileId(),
                profile.getId(),
                profile.getSource() == null ? null : profile.getSource().getDatasourceRef(),
                profile.getSource() == null ? null : profile.getSource().describeType(datasourceCatalog),
                profile.getTarget() == null ? null : profile.getTarget().getDatasourceRef(),
                profile.getTarget() == null ? null : profile.getTarget().describeType(datasourceCatalog),
                key == null ? null : key.getType().name(),
                key == null ? List.of() : key.getColumns(),
                profile.getHashingStrategy() == null ? null : profile.getHashingStrategy().name(),
                profile.getSchedule(),
                profile.resolvedRecon().resolvedMode().name(),
                profile.resolvedRecon().resolvedConditionFields()
        );
    }
}
