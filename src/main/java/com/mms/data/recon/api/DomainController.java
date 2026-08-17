package com.mms.data.recon.api;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.mms.data.recon.config.DatasourceCatalog;
import com.mms.data.recon.config.RecConfiguration;
import com.mms.data.recon.dataset.DatasetConfiguration;
import com.mms.data.recon.dataset.DomainConfiguration;

import java.net.URI;
import java.util.List;

@Tag(name = "Domains")
@Controller("/api")
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

    @Get("/domains")
    public List<DomainApiModel> listDomains() {
        return configuration.getDomains().values().stream()
                .map(this::domain)
                .toList();
    }

    @Post("/domains")
    public HttpResponse<DomainApiModel> createDomain(@Body DomainUpsertRequest request) {
        DomainApiModel created = domain(catalog.createDomain(request));
        return HttpResponse.created(URI.create("/api/domains/" + created.id())).body(created);
    }

    @Get("/domains/{domainId}")
    public DomainApiModel getDomain(@PathVariable String domainId) {
        return domain(configuration.requireDomain(domainId));
    }

    @Put("/domains/{domainId}")
    public DomainApiModel updateDomain(@PathVariable String domainId, @Body DomainUpsertRequest request) {
        return domain(catalog.updateDomain(domainId, request));
    }

    @Delete("/domains/{domainId}")
    public HttpResponse<Void> deleteDomain(@PathVariable String domainId) {
        catalog.deleteDomain(domainId);
        return HttpResponse.noContent();
    }

    @Get("/domains/{domainId}/profiles")
    public List<ProfileApiModel> listProfiles(@PathVariable String domainId) {
        return configuration.requireDomain(domainId).getProfiles().values().stream()
                .map(this::profile)
                .toList();
    }

    @Post("/domains/{domainId}/profiles")
    public HttpResponse<ProfileApiModel> createProfile(
            @PathVariable String domainId,
            @Body ProfileUpsertRequest request) {
        ProfileApiModel created = profile(catalog.createProfile(domainId, request));
        return HttpResponse.created(URI.create("/api/domains/" + domainId + "/profiles/" + created.profileId()))
                .body(created);
    }

    @Get("/domains/{domainId}/profiles/{profileId}")
    public ProfileApiModel getProfile(@PathVariable String domainId, @PathVariable String profileId) {
        return profile(configuration.requireProfile(domainId, profileId));
    }

    @Put("/domains/{domainId}/profiles/{profileId}")
    public ProfileApiModel updateProfile(
            @PathVariable String domainId,
            @PathVariable String profileId,
            @Body ProfileUpsertRequest request) {
        return profile(catalog.updateProfile(domainId, profileId, request));
    }

    @Delete("/domains/{domainId}/profiles/{profileId}")
    public HttpResponse<Void> deleteProfile(@PathVariable String domainId, @PathVariable String profileId) {
        catalog.deleteProfile(domainId, profileId);
        return HttpResponse.noContent();
    }

    @Put("/domains/{domainId}/profiles/{profileId}/datasources")
    public ProfileApiModel attachDatasources(
            @PathVariable String domainId,
            @PathVariable String profileId,
            @Body AttachDatasourcesRequest request) {
        return profile(catalog.attachDatasources(domainId, profileId, request));
    }

    @Put("/domains/{domainId}/profiles/{profileId}/source")
    public ProfileApiModel updateSource(
            @PathVariable String domainId,
            @PathVariable String profileId,
            @Body SideRequest request) {
        return profile(catalog.updateSource(domainId, profileId, request));
    }

    @Put("/domains/{domainId}/profiles/{profileId}/target")
    public ProfileApiModel updateTarget(
            @PathVariable String domainId,
            @PathVariable String profileId,
            @Body SideRequest request) {
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
