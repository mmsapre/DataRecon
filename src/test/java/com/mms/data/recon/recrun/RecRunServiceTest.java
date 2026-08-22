package com.mms.data.recon.recrun;

import com.mms.data.recon.config.RecConfiguration;
import com.mms.data.recon.dataset.DataLoadDefinition;
import com.mms.data.recon.dataset.DatasetConfiguration;
import com.mms.data.recon.dataset.DatasetRecService;
import com.mms.data.recon.dataset.DomainConfiguration;
import com.mms.data.recon.dataset.DuckDbExceptReconciler;
import com.mms.data.recon.dataset.HashingStrategy;
import com.mms.data.recon.dataset.InMemoryRecStores;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecRunServiceTest {

    @Test
    void throwsOnUnknownProfile() {
        RecRunService service = new RecRunService(
                new RecConfiguration(),
                new DatasetRecService(
                        new InMemoryRecStores.ScriptedRowLoader(),
                        new InMemoryRecStores.MemoryRecRunRepository(),
                        new InMemoryRecStores.MemoryRecRecordRepository(),
                        new DuckDbExceptReconciler()
                ),
                new InMemoryRecStores.MemoryRecRunRepository(),
                new InMemoryRecStores.MemoryRecRecordRepository()
        );

        assertThrows(IllegalArgumentException.class, () -> service.runProfile("party", "missing").block());
    }

    @Test
    void runProfileDelegatesToDatasetServiceAndListsResults() {
        RecConfiguration configuration = configurationWithProfiles(
                InMemoryRecStores.profile("party", "pg-pg", "source", "target")
        );

        InMemoryRecStores.MemoryRecRunRepository runs = new InMemoryRecStores.MemoryRecRunRepository();
        InMemoryRecStores.MemoryRecRecordRepository records = new InMemoryRecStores.MemoryRecRecordRepository();
        DatasetRecService recService = new DatasetRecService(
                new InMemoryRecStores.ScriptedRowLoader()
                        .put("source", List.of(InMemoryRecStores.row("k", "v")))
                        .put("target", List.of(InMemoryRecStores.row("k", "v"))),
                runs,
                records,
                new DuckDbExceptReconciler()
        );
        RecRunService service = new RecRunService(configuration, recService, runs, records);

        Long runId = service.runProfile("party", "pg-pg").block();
        assertEquals(1L, runId);
        InMemoryRecStores.awaitTerminal(runs, runId);
        assertEquals(1, service.profileRuns("party", "pg-pg").size());
        assertEquals("pg-pg", service.profileRuns("party", "pg-pg").get(0).profileId());
        assertTrue(service.profileRuns("party", "pg-pg").get(0).active());
        assertEquals(1, service.profileRuns("party", "pg-pg").get(0).matched());
        assertEquals(0, service.records(runId, "MATCHED").size());
    }

    @Test
    void runDomainTriggersEveryProfileAndAggregatesResults() {
        RecConfiguration configuration = configurationWithProfiles(
                InMemoryRecStores.profile("party", "pg-pg", "landing", "master"),
                InMemoryRecStores.profile("party", "pg-mongo", "landing", "mongo")
        );

        InMemoryRecStores.MemoryRecRunRepository runs = new InMemoryRecStores.MemoryRecRunRepository();
        InMemoryRecStores.MemoryRecRecordRepository records = new InMemoryRecStores.MemoryRecRecordRepository();
        DatasetRecService recService = new DatasetRecService(
                new InMemoryRecStores.ScriptedRowLoader()
                        .put("landing", List.of(InMemoryRecStores.row("k", "v")))
                        .put("master", List.of(InMemoryRecStores.row("k", "v")))
                        .put("mongo", List.of(InMemoryRecStores.row("k", "v"))),
                runs,
                records,
                new DuckDbExceptReconciler()
        );
        RecRunService service = new RecRunService(configuration, recService, runs, records);

        RecRunService.DomainRunResult result = service.runDomain("party").block();
        assertEquals("party", result.domainId());
        assertEquals(1L, result.domainRunId());
        assertEquals(2, result.runIds().size());
        assertEquals(2L, result.runIds().get("pg-pg"));
        assertEquals(3L, result.runIds().get("pg-mongo"));

        InMemoryRecStores.awaitTerminal(runs, result.domainRunId());
        RecRunService.DomainRunDetail detail = service.domainRun("party", result.domainRunId());
        assertNull(detail.domain().profileId());
        assertEquals("COMPLETED", detail.domain().status());
        assertEquals(2, detail.profiles().size());
        assertEquals(2, detail.domain().matched());
        assertEquals(3, service.domainRuns("party").size());
        assertEquals(1, service.profileRuns("party", "pg-pg").size());
    }

    @Test
    void latestCompletedRunIsActiveAndPreviousRunsAreInactive() {
        RecConfiguration configuration = configurationWithProfiles(
                InMemoryRecStores.profile("party", "pg-pg", "source", "target")
        );
        InMemoryRecStores.MemoryRecRunRepository runs = new InMemoryRecStores.MemoryRecRunRepository();
        InMemoryRecStores.MemoryRecRecordRepository records = new InMemoryRecStores.MemoryRecRecordRepository();
        DatasetRecService recService = new DatasetRecService(
                new InMemoryRecStores.ScriptedRowLoader()
                        .put("source", List.of(InMemoryRecStores.row("k", "v")))
                        .put("target", List.of(InMemoryRecStores.row("k", "v"))),
                runs,
                records,
                new DuckDbExceptReconciler()
        );
        RecRunService service = new RecRunService(configuration, recService, runs, records);

        Long first = service.runProfile("party", "pg-pg").block();
        InMemoryRecStores.awaitTerminal(runs, first);
        Long second = service.runProfile("party", "pg-pg").block();
        InMemoryRecStores.awaitTerminal(runs, second);

        assertFalse(runs.find(first).active());
        assertTrue(runs.find(second).active());
        assertEquals(1, service.profileRuns("party", "pg-pg", true).size());
        assertEquals(second, service.profileRuns("party", "pg-pg", true).get(0).id());
    }

    @Test
    void resolveProfileByQualifiedIdNameOrDomainHint() {
        RecConfiguration configuration = configurationWithProfiles(
                InMemoryRecStores.profile("party", "pg-pg", "source", "target"),
                InMemoryRecStores.profile("party", "pg-mongo", "landing", "mongo")
        );
        RecRunService service = serviceSettings(configuration);

        assertEquals("party.pg-mongo", service.resolveProfile(null, "party.pg-mongo").getId());
        assertEquals("pg-pg", service.resolveProfile(null, "pg-pg").getProfileId());
        assertEquals("pg-mongo", service.resolveProfile("party", "pg-mongo").getProfileId());
        assertThrows(IllegalArgumentException.class, () -> service.resolveProfile(null, "missing"));
    }

    @Test
    void runResolvedProfileForcesCountsOrDetails() {
        RecConfiguration configuration = configurationWithProfiles(
                InMemoryRecStores.profile("party", "pg-pg", "source", "target")
        );
        InMemoryRecStores.MemoryRecRunRepository runs = new InMemoryRecStores.MemoryRecRunRepository();
        InMemoryRecStores.MemoryRecRecordRepository records = new InMemoryRecStores.MemoryRecRecordRepository();
        DatasetRecService recService = new DatasetRecService(
                new InMemoryRecStores.ScriptedRowLoader()
                        .put("source", List.of(InMemoryRecStores.row("k", "v")))
                        .put("target", List.of(InMemoryRecStores.row("k", "v"))),
                runs,
                records,
                new DuckDbExceptReconciler()
        );
        RecRunService service = new RecRunService(configuration, recService, runs, records);

        RecRunService.ProfileTriggerResult counts = service.runResolvedProfile(
                null, "pg-pg", com.mms.data.recon.dataset.ReconMode.COUNTS, null, false).block();
        InMemoryRecStores.awaitTerminal(runs, counts.runId());
        assertEquals(com.mms.data.recon.dataset.ReconMode.COUNTS, counts.mode());
        assertEquals("COUNTS", runs.find(counts.runId()).reconMode());

        RecRunService.ProfileTriggerResult details = service.runResolvedProfile(
                "party", "pg-pg", com.mms.data.recon.dataset.ReconMode.MISMATCH_DETAILS, null, false).block();
        InMemoryRecStores.awaitTerminal(runs, details.runId());
        assertEquals(com.mms.data.recon.dataset.ReconMode.MISMATCH_DETAILS, details.mode());
        assertEquals("MISMATCH_DETAILS", runs.find(details.runId()).reconMode());
    }

    @Test
    void controllerReconSettingsAreUsedForSubsequentRuns() {
        RecConfiguration configuration = configurationWithProfiles(
                InMemoryRecStores.profile("party", "pg-pg", "source", "target")
        );
        serviceSettings(configuration).applyProfileRecon("party", "pg-pg", com.mms.data.recon.dataset.ReconMode.COUNTS, List.of());
        assertEquals(com.mms.data.recon.dataset.ReconMode.COUNTS, configuration.requireProfile("party", "pg-pg").resolvedRecon().resolvedMode());
    }

    private static RecRunService serviceSettings(RecConfiguration configuration) {
        return new RecRunService(
                configuration,
                new DatasetRecService(
                        new InMemoryRecStores.ScriptedRowLoader(),
                        new InMemoryRecStores.MemoryRecRunRepository(),
                        new InMemoryRecStores.MemoryRecRecordRepository(),
                        new DuckDbExceptReconciler()
                ),
                new InMemoryRecStores.MemoryRecRunRepository(),
                new InMemoryRecStores.MemoryRecRecordRepository()
        );
    }

    private static RecConfiguration configurationWithProfiles(DatasetConfiguration... profiles) {
        DomainConfiguration domain = new DomainConfiguration();
        Map<String, DatasetConfiguration> profileMap = new LinkedHashMap<>();
        for (DatasetConfiguration profile : profiles) {
            profileMap.put(profile.getProfileId(), copy(profile));
        }
        domain.setProfiles(profileMap);

        RecConfiguration configuration = new RecConfiguration();
        Map<String, DomainConfiguration> domains = new LinkedHashMap<>();
        domains.put("party", domain);
        configuration.setDomains(domains);
        return configuration;
    }

    private static DatasetConfiguration copy(DatasetConfiguration source) {
        DatasetConfiguration copy = new DatasetConfiguration();
        DataLoadDefinition src = new DataLoadDefinition();
        src.setDatasourceRef(source.getSource().getDatasourceRef());
        DataLoadDefinition tgt = new DataLoadDefinition();
        tgt.setDatasourceRef(source.getTarget().getDatasourceRef());
        copy.setSource(src);
        copy.setTarget(tgt);
        copy.setHashingStrategy(HashingStrategy.TypeLenient);
        copy.setBatchSize(10);
        return copy;
    }
}
