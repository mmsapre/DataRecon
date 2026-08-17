package com.mms.data.recon.recrun;

import jakarta.inject.Singleton;
import com.mms.data.recon.config.RecConfiguration;
import com.mms.data.recon.dataset.DatasetRecService;
import com.mms.data.recon.dataset.DomainConfiguration;
import com.mms.data.recon.dataset.ReconSettings;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
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
        return runProfile(domainId, profileId, null, null);
    }

    public Mono<Long> runProfile(
            String domainId,
            String profileId,
            com.mms.data.recon.dataset.ReconMode mode,
            List<String> conditionFields) {
        var profile = configuration.requireProfile(domainId, profileId);
        ReconSettings settings = profile.resolvedRecon().overlay(mode, conditionFields);
        return datasetRecService.reconcile(profile, null, settings);
    }

    public Mono<DomainRunResult> runDomain(String domainId) {
        return runDomain(domainId, null, null);
    }

    public Mono<DomainRunResult> runDomain(
            String domainId,
            com.mms.data.recon.dataset.ReconMode mode,
            List<String> conditionFields) {
        DomainConfiguration domain = configuration.requireDomain(domainId);
        long domainRunId = runRepository.createDomainRun(domainId);
        return Flux.fromIterable(domain.getProfiles().entrySet())
                .concatMap(entry -> {
                    ReconSettings settings = entry.getValue().resolvedRecon().overlay(mode, conditionFields);
                    return datasetRecService
                            .reconcile(entry.getValue(), domainRunId, settings)
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
            com.mms.data.recon.dataset.ReconMode mode,
            List<String> conditionFields) {
        ReconSettings recon = configuration.requireProfile(domainId, profileId).resolvedRecon();
        recon.apply(mode, conditionFields);
        return recon;
    }

    public ReconSettings applyDomainRecon(
            String domainId,
            com.mms.data.recon.dataset.ReconMode mode,
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
}
