package com.mms.data.recon.api;

import com.mms.data.recon.config.ConfigurationException;
import com.mms.data.recon.config.DatasourceCatalog;
import com.mms.data.recon.config.RecConfiguration;
import com.mms.data.recon.dataset.DataLoadDefinition;
import com.mms.data.recon.dataset.DatasetConfiguration;
import com.mms.data.recon.dataset.DatasetRecScheduler;
import com.mms.data.recon.dataset.DomainConfiguration;
import com.mms.data.recon.dataset.ProfileDatasources;
import com.mms.data.recon.dataset.ReconSettings;
import jakarta.annotation.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RecCatalogService {

    private final RecConfiguration configuration;
    private final DatasourceCatalog catalog;
    private final DatasetRecScheduler scheduler;

    public RecCatalogService(
            RecConfiguration configuration,
            DatasourceCatalog catalog,
            @Nullable DatasetRecScheduler scheduler) {
        this.configuration = configuration;
        this.catalog = catalog;
        this.scheduler = scheduler;
    }

    public synchronized DomainConfiguration createDomain(DomainUpsertRequest request) {
        String id = requireBodyId(request == null ? null : request.getId(), "domain");
        if (configuration.getDomains().containsKey(id)) {
            throw conflict("Domain already exists: " + id);
        }
        DomainConfiguration domain = new DomainConfiguration();
        applyDomain(domain, request, true);
        try {
            domain.initialize(id, configuration.getDefaults(), false);
        } catch (RuntimeException e) {
            throw wrap(e);
        }
        configuration.putDomain(id, domain);
        sync(domain);
        return domain;
    }

    public synchronized DomainConfiguration updateDomain(String domainId, DomainUpsertRequest request) {
        DomainConfiguration domain = requireDomain(domainId);
        applyDomain(domain, request, false);
        sync(domain);
        return domain;
    }

    public synchronized DomainConfiguration deleteDomain(String domainId) {
        DomainConfiguration domain = requireDomain(domainId);
        cancel(domainId);
        return configuration.removeDomain(domainId);
    }

    public synchronized DatasetConfiguration createProfile(String domainId, ProfileUpsertRequest request) {
        DomainConfiguration domain = requireDomain(domainId);
        String profileId = requireBodyId(request == null ? null : request.getId(), "profile");
        if (domain.getProfiles().containsKey(profileId)) {
            throw conflict("Profile already exists: " + domainId + "." + profileId);
        }
        DatasetConfiguration profile = new DatasetConfiguration();
        applyProfile(profile, request, true);
        DatasetConfiguration stored = storeProfile(domainId, profileId, profile);
        sync(domain);
        return stored;
    }

    public synchronized DatasetConfiguration updateProfile(
            String domainId,
            String profileId,
            ProfileUpsertRequest request) {
        DatasetConfiguration profile = requireProfile(domainId, profileId);
        applyProfile(profile, request, false);
        DatasetConfiguration stored = storeProfile(domainId, profileId, profile);
        sync(configuration.requireDomain(domainId));
        return stored;
    }

    public synchronized DatasetConfiguration deleteProfile(String domainId, String profileId) {
        requireProfile(domainId, profileId);
        DatasetConfiguration removed = configuration.removeProfile(domainId, profileId);
        sync(configuration.requireDomain(domainId));
        return removed;
    }

    public synchronized DatasetConfiguration attachDatasources(
            String domainId,
            String profileId,
            AttachDatasourcesRequest request) {
        if (request == null || (blank(request.getSource()) && blank(request.getTarget()))) {
            throw badRequest("source and/or target datasource name is required");
        }
        DatasetConfiguration profile = requireProfile(domainId, profileId);
        attachNamed(profile, request.getSource(), request.getTarget());
        DatasetConfiguration stored = storeProfile(domainId, profileId, profile);
        sync(configuration.requireDomain(domainId));
        return stored;
    }

    public synchronized DatasetConfiguration updateSource(String domainId, String profileId, SideRequest request) {
        return updateSide(domainId, profileId, true, request);
    }

    public synchronized DatasetConfiguration updateTarget(String domainId, String profileId, SideRequest request) {
        return updateSide(domainId, profileId, false, request);
    }

    private DatasetConfiguration updateSide(
            String domainId,
            String profileId,
            boolean source,
            SideRequest request) {
        if (request == null) {
            throw badRequest((source ? "source" : "target") + " body is required");
        }
        DatasetConfiguration profile = requireProfile(domainId, profileId);
        ensureSides(profile);
        if (!blank(request.getDatasource())) {
            requireKnownDatasource(request.getDatasource());
        }
        request.applyTo(source ? profile.getSource() : profile.getTarget());
        DatasetConfiguration stored = storeProfile(domainId, profileId, profile);
        sync(configuration.requireDomain(domainId));
        return stored;
    }

    private void applyDomain(DomainConfiguration domain, DomainUpsertRequest request, boolean creating) {
        if (request == null) {
            if (creating) {
                throw badRequest("Domain body is required");
            }
            return;
        }
        validateSchedule(request.getSchedule());
        if (request.getSchedule() != null) {
            domain.setSchedule(blank(request.getSchedule()) ? null : request.getSchedule());
        }
        if (request.getHashingStrategy() != null) {
            domain.setHashingStrategy(request.getHashingStrategy());
        }
        if (request.getBatchSize() != null) {
            domain.setBatchSize(request.getBatchSize());
        }
        if (request.getBatchConcurrency() != null) {
            domain.setBatchConcurrency(request.getBatchConcurrency());
        }
        if (request.getRecon() != null) {
            applyRecon(domain.getRecon(), request.getRecon());
            if (!creating) {
                domain.getProfiles().values().forEach(profile ->
                        applyRecon(profile.resolvedRecon(), request.getRecon()));
            }
        }
    }

    private void applyProfile(DatasetConfiguration profile, ProfileUpsertRequest request, boolean creating) {
        if (request == null) {
            if (creating) {
                throw badRequest("Profile body is required");
            }
            return;
        }
        validateSchedule(request.getSchedule());
        if (request.getSchedule() != null) {
            profile.setSchedule(blank(request.getSchedule()) ? null : request.getSchedule());
        }
        if (request.getHashingStrategy() != null) {
            profile.setHashingStrategy(request.getHashingStrategy());
        }
        if (request.getBatchSize() != null) {
            profile.setBatchSize(request.getBatchSize());
        }
        if (request.getBatchConcurrency() != null) {
            profile.setBatchConcurrency(request.getBatchConcurrency());
        }
        if (request.getMigrationKey() != null) {
            profile.setMigrationKey(request.getMigrationKey());
        }
        if (request.getRecon() != null) {
            applyRecon(profile.resolvedRecon(), request.getRecon());
        }
        if (creating) {
            ensureSides(profile);
        }
        if (request.getSource() != null) {
            ensureSides(profile);
            if (!blank(request.getSource().getDatasource())) {
                requireKnownDatasource(request.getSource().getDatasource());
            }
            request.getSource().applyTo(profile.getSource());
        }
        if (request.getTarget() != null) {
            ensureSides(profile);
            if (!blank(request.getTarget().getDatasource())) {
                requireKnownDatasource(request.getTarget().getDatasource());
            }
            request.getTarget().applyTo(profile.getTarget());
        }
        if (request.getDatasources() != null) {
            attachNamed(profile, request.getDatasources().getSource(), request.getDatasources().getTarget());
        }
        if (creating) {
            ensureSides(profile);
        }
    }

    private void attachNamed(DatasetConfiguration profile, String sourceRef, String targetRef) {
        ensureSides(profile);
        ProfileDatasources datasources = profile.getDatasources();
        if (!blank(sourceRef)) {
            requireKnownDatasource(sourceRef);
            datasources.setSource(sourceRef);
            profile.getSource().attachDatasource(sourceRef);
        }
        if (!blank(targetRef)) {
            requireKnownDatasource(targetRef);
            datasources.setTarget(targetRef);
            profile.getTarget().attachDatasource(targetRef);
        }
    }

    private static void ensureSides(DatasetConfiguration profile) {
        if (profile.getSource() == null) {
            profile.setSource(new DataLoadDefinition());
        }
        if (profile.getTarget() == null) {
            profile.setTarget(new DataLoadDefinition());
        }
        if (profile.getDatasources() == null) {
            profile.setDatasources(new ProfileDatasources());
        }
    }

    private DatasetConfiguration storeProfile(String domainId, String profileId, DatasetConfiguration profile) {
        try {
            return configuration.putProfile(domainId, profileId, profile);
        } catch (RuntimeException e) {
            throw wrap(e);
        }
    }

    private DomainConfiguration requireDomain(String domainId) {
        try {
            return configuration.requireDomain(domainId);
        } catch (IllegalArgumentException e) {
            throw wrap(e);
        }
    }

    private DatasetConfiguration requireProfile(String domainId, String profileId) {
        try {
            return configuration.requireProfile(domainId, profileId);
        } catch (IllegalArgumentException e) {
            throw wrap(e);
        }
    }

    private void requireKnownDatasource(String name) {
        try {
            catalog.require(name);
        } catch (ConfigurationException e) {
            throw badRequest(e.getMessage());
        }
    }

    private void validateSchedule(String schedule) {
        if (blank(schedule)) {
            return;
        }
        try {
            DatasetRecScheduler.parseSeconds(schedule);
        } catch (RuntimeException e) {
            throw badRequest("Invalid schedule: " + schedule);
        }
    }

    private static void applyRecon(ReconSettings settings, ReconRunRequest request) {
        settings.apply(request.getMode(), request.getConditionFields());
    }

    private void sync(DomainConfiguration domain) {
        if (scheduler != null) {
            scheduler.syncDomain(domain);
        }
    }

    private void cancel(String domainId) {
        if (scheduler != null) {
            scheduler.cancelDomain(domainId);
        }
    }

    private static String requireBodyId(String id, String field) {
        try {
            return DomainConfiguration.requireName(field, id);
        } catch (IllegalArgumentException e) {
            throw badRequest(e.getMessage());
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ResponseStatusException wrap(RuntimeException e) {
        if (e instanceof ResponseStatusException status) {
            return status;
        }
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (e instanceof IllegalArgumentException && message.startsWith("Unknown")) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
