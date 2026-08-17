package com.mms.data.recon.api;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.mms.data.recon.config.DatasourceCatalog;
import com.mms.data.recon.config.RecConfiguration;
import com.mms.data.recon.dataset.DatasetConfiguration;
import com.mms.data.recon.dataset.DomainConfiguration;

import java.util.List;

@Tag(name = "Domains")
@Controller("/api")
public class DomainController {

    private final RecConfiguration configuration;
    private final DatasourceCatalog datasourceCatalog;

    public DomainController(RecConfiguration configuration, DatasourceCatalog datasourceCatalog) {
        this.configuration = configuration;
        this.datasourceCatalog = datasourceCatalog;
    }

    @Get("/domains")
    public List<DomainApiModel> listDomains() {
        return configuration.getDomains().values().stream()
                .map(this::domain)
                .toList();
    }

    @Get("/domains/{domainId}")
    public DomainApiModel getDomain(@PathVariable String domainId) {
        return domain(configuration.requireDomain(domainId));
    }

    @Get("/domains/{domainId}/profiles")
    public List<ProfileApiModel> listProfiles(@PathVariable String domainId) {
        return configuration.requireDomain(domainId).getProfiles().values().stream()
                .map(this::profile)
                .toList();
    }

    @Get("/domains/{domainId}/profiles/{profileId}")
    public ProfileApiModel getProfile(@PathVariable String domainId, @PathVariable String profileId) {
        return profile(configuration.requireProfile(domainId, profileId));
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
                : profile.getSource().getMigrationKey();
        return new ProfileApiModel(
                profile.getDomainId(),
                profile.getProfileId(),
                profile.getId(),
                profile.getSource().getDatasourceRef(),
                profile.getSource().describeType(datasourceCatalog),
                profile.getTarget().getDatasourceRef(),
                profile.getTarget().describeType(datasourceCatalog),
                key == null ? null : key.getType().name(),
                key == null ? List.of() : key.getColumns(),
                profile.getHashingStrategy().name(),
                profile.getSchedule(),
                profile.resolvedRecon().resolvedMode().name(),
                profile.resolvedRecon().resolvedConditionFields()
        );
    }
}
