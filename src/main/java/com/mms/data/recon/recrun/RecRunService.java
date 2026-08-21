package com.mms.data.recon.recrun;

import com.mms.data.recon.config.RecConfiguration;
import com.mms.data.recon.dataset.DatasetConfiguration;
import com.mms.data.recon.dataset.DatasetRecService;
import com.mms.data.recon.dataset.DomainConfiguration;
import com.mms.data.recon.dataset.ReconMode;
import com.mms.data.recon.dataset.ReconSettings;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class RecRunService {

    private final RecConfiguration configuration;
    private final DatasetRecService datasetRecService;
    private final RecRunRepository runRepository;
    private final RecRecordRepository recordRepository;

    public RecRunService(
            RecConfiguration configuration,
            DatasetRecService datasetRecService,
            RecRunRepository runRepository,
            RecRecordRepository recordRepository) {
        this.configuration = configuration;
        this.datasetRecService = datasetRecService;
        this.runRepository = runRepository;
        this.recordRepository = recordRepository;
    }

    public Mono<Long> runProfile(String domainId, String profileId) {
        return runProfile(domainId, profileId, null, null, false);
    }

    public Mono<Long> runProfile(
            String domainId,
            String profileId,
            ReconMode mode,
            List<String> conditionFields) {
        return runProfile(domainId, profileId, mode, conditionFields, false);
    }

    public Mono<Long> runProfile(
            String domainId,
            String profileId,
            ReconMode mode,
            List<String> conditionFields,
            boolean forceFull) {
        var profile = configuration.requireProfile(domainId, profileId);
        ReconSettings settings = profile.resolvedRecon().overlay(mode, conditionFields);
        return datasetRecService.reconcile(profile, null, settings, forceFull);
    }

    /**
     * Resolve a profile by qualified id ({@code domain.profile}), profile id, or
     * optional domain + profile name, then run with an explicit mode.
     */
    public Mono<ProfileTriggerResult> runResolvedProfile(
            String domainHint,
            String profileRef,
            ReconMode mode,
            List<String> conditionFields,
            boolean forceFull) {
        DatasetConfiguration profile = resolveProfile(domainHint, profileRef);
        ReconSettings settings = profile.resolvedRecon().overlay(mode, conditionFields);
        return datasetRecService.reconcile(profile, null, settings, forceFull)
                .map(runId -> new ProfileTriggerResult(
                        profile.getDomainId(),
                        profile.getProfileId(),
                        profile.getId(),
                        mode == null ? settings.resolvedMode() : mode,
                        runId));
    }

    public DatasetConfiguration resolveProfile(String domainHint, String profileRef) {
        String ref = blankToNull(profileRef);
        if (ref == null) {
            throw new IllegalArgumentException("profile is required (id, name, or domain.profile)");
        }
        String domain = blankToNull(domainHint);

        int dot = ref.indexOf('.');
        if (dot > 0 && dot < ref.length() - 1) {
            String domainPart = ref.substring(0, dot);
            String profilePart = ref.substring(dot + 1);
            if (domain != null && !domain.equalsIgnoreCase(domainPart)) {
                throw new IllegalArgumentException(
                        "domain '" + domain + "' does not match profile ref '" + ref + "'");
            }
            return configuration.requireProfile(domainPart, profilePart);
        }

        if (domain != null) {
            return configuration.requireProfile(domain, ref);
        }

        List<DatasetConfiguration> matches = configuration.allProfiles().stream()
                .filter(profile -> matchesRef(profile, ref))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Unknown profile: " + ref);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    "Ambiguous profile '" + ref + "'; qualify as domain.profile or pass domain");
        }
        return matches.get(0);
    }

    private static boolean matchesRef(DatasetConfiguration profile, String ref) {
        String needle = ref.toLowerCase(Locale.ROOT);
        return Objects.equals(lower(profile.getProfileId()), needle)
                || Objects.equals(lower(profile.getId()), needle);
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public Mono<DomainRunResult> runDomain(String domainId) {
        return runDomain(domainId, null, null, false);
    }

    public Mono<DomainRunResult> runDomain(
            String domainId,
            ReconMode mode,
            List<String> conditionFields) {
        return runDomain(domainId, mode, conditionFields, false);
    }

    public Mono<DomainRunResult> runDomain(
            String domainId,
            ReconMode mode,
            List<String> conditionFields,
            boolean forceFull) {
        DomainConfiguration domain = configuration.requireDomain(domainId);
        long domainRunId = runRepository.createDomainRun(domainId);
        return Flux.fromIterable(domain.getProfiles().entrySet())
                .concatMap(entry -> {
                    ReconSettings settings = entry.getValue().resolvedRecon().overlay(mode, conditionFields);
                    return datasetRecService
                            .reconcile(entry.getValue(), domainRunId, settings, forceFull)
                            .map(runId -> Map.entry(entry.getKey(), runId));
                })
                .collectMap(Map.Entry::getKey, Map.Entry::getValue, LinkedHashMap::new)
                .doOnSuccess(ignored -> completeDomainRun(domainRunId))
                .doOnError(error -> runRepository.fail(domainRunId, error))
                .map(runIds -> new DomainRunResult(domainId, domainRunId, runIds));
    }

    public List<RecRunRepository.RunView> runs(String datasetId) {
        return runRepository.list(datasetId);
    }

    public List<RecRunRepository.RunView> domainRuns(String domainId) {
        return domainRuns(domainId, null);
    }

    public List<RecRunRepository.RunView> domainRuns(String domainId, Boolean active) {
        return runRepository.listByDomain(configuration.requireDomain(domainId).getId(), active);
    }

    public List<RecRunRepository.RunView> profileRuns(String domainId, String profileId) {
        return profileRuns(domainId, profileId, null);
    }

    public List<RecRunRepository.RunView> profileRuns(String domainId, String profileId, Boolean active) {
        configuration.requireProfile(domainId, profileId);
        return runRepository.listByProfile(domainId, profileId, active);
    }

    public ReconSettings applyProfileRecon(
            String domainId,
            String profileId,
            ReconMode mode,
            List<String> conditionFields) {
        ReconSettings recon = configuration.requireProfile(domainId, profileId).resolvedRecon();
        recon.apply(mode, conditionFields);
        return recon;
    }

    public ReconSettings applyDomainRecon(
            String domainId,
            ReconMode mode,
            List<String> conditionFields) {
        DomainConfiguration domain = configuration.requireDomain(domainId);
        domain.getRecon().apply(mode, conditionFields);
        domain.getProfiles().values().forEach(profile -> profile.resolvedRecon().apply(mode, conditionFields));
        return domain.getRecon();
    }

    public DomainRunDetail domainRun(String domainId, long domainRunId) {
        configuration.requireDomain(domainId);
        RecRunRepository.RunView parent = runRepository.find(domainRunId);
        if (parent == null || parent.profileId() != null || !domainId.equals(parent.domainId())) {
            throw new IllegalArgumentException("Unknown domain run: " + domainRunId + " for domain " + domainId);
        }
        List<RecRunRepository.RunView> profiles = runRepository.listByDomainRun(domainRunId).stream()
                .filter(run -> run.profileId() != null)
                .toList();
        return new DomainRunDetail(parent, profiles);
    }

    public List<RecRecordRepository.RecRecord> records(long runId, String status) {
        return recordRepository.findByRun(runId, status);
    }

    public RecRecordRepository.Page recordsPage(long runId, String status, int limit, int offset) {
        return recordRepository.findByRun(runId, status, limit, offset);
    }

    private void completeDomainRun(long domainRunId) {
        List<RecRunRepository.RunView> profiles = runRepository.listByDomainRun(domainRunId).stream()
                .filter(run -> run.profileId() != null)
                .toList();
        boolean failed = profiles.stream().anyMatch(run -> "FAILED".equals(run.status()));
        if (failed) {
            runRepository.fail(domainRunId, new IllegalStateException("One or more profiles failed"));
            return;
        }
        runRepository.complete(domainRunId, RecRunRepository.RunSummary.of(profiles));
    }

    public record DomainRunResult(String domainId, long domainRunId, Map<String, Long> runIds) {}

    public record DomainRunDetail(RecRunRepository.RunView domain, List<RecRunRepository.RunView> profiles) {}

    public record ProfileTriggerResult(
            String domainId,
            String profileId,
            String id,
            ReconMode mode,
            long runId) {}
}
