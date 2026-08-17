package com.mms.data.recon.dataset;

import com.mms.data.recon.recrun.RecRunRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetRecServiceTest {

    @Test
    void reconcilesEmptyDatasetsWithoutError() {
        InMemoryRecStores.ScriptedRowLoader loader = new InMemoryRecStores.ScriptedRowLoader();
        InMemoryRecStores.MemoryRecRunRepository runs = new InMemoryRecStores.MemoryRecRunRepository();
        InMemoryRecStores.MemoryRecRecordRepository records = new InMemoryRecStores.MemoryRecRecordRepository();
        DatasetRecService service = new DatasetRecService(loader, runs, records);

        Long runId = service.reconcile(InMemoryRecStores.dataset("empty", List.of(), List.of())).block();

        assertEquals(1L, runId);
        assertEquals(0, records.inserted.size());
        assertEquals(0, runs.lastSummary.sourceCount());
        assertEquals(0, runs.lastSummary.targetCount());
        assertEquals(0, runs.lastSummary.matched());
    }

    @Test
    void reconcilesSourceWithEmptyTargetAsSourceOnly() {
        InMemoryRecStores.ScriptedRowLoader loader = new InMemoryRecStores.ScriptedRowLoader()
                .put("source", List.of(InMemoryRecStores.row("abc", "def")))
                .put("target", List.of());
        InMemoryRecStores.MemoryRecRunRepository runs = new InMemoryRecStores.MemoryRecRunRepository();
        InMemoryRecStores.MemoryRecRecordRepository records = new InMemoryRecStores.MemoryRecRecordRepository();

        new DatasetRecService(loader, runs, records)
                .reconcile(InMemoryRecStores.dataset("src-only", List.of(), List.of()))
                .block();

        assertEquals(1, records.inserted.size());
        assertEquals(com.mms.data.recon.recrun.RecRecordRepository.RecStatus.SOURCE_ONLY, records.inserted.get(0).status());
        assertEquals(1, runs.lastSummary.sourceOnly());
    }

    @Test
    void reconcilesEmptySourceWithTargetAsTargetOnly() {
        InMemoryRecStores.ScriptedRowLoader loader = new InMemoryRecStores.ScriptedRowLoader()
                .put("source", List.of())
                .put("target", List.of(InMemoryRecStores.row("abc", "def")));
        InMemoryRecStores.MemoryRecRunRepository runs = new InMemoryRecStores.MemoryRecRunRepository();
        InMemoryRecStores.MemoryRecRecordRepository records = new InMemoryRecStores.MemoryRecRecordRepository();

        new DatasetRecService(loader, runs, records)
                .reconcile(InMemoryRecStores.dataset("tgt-only", List.of(), List.of()))
                .block();

        assertEquals(com.mms.data.recon.recrun.RecRecordRepository.RecStatus.TARGET_ONLY, records.inserted.get(0).status());
        assertEquals(1, runs.lastSummary.targetOnly());
    }

    @Test
    void reconcilesMatchingHashes() {
        InMemoryRecStores.ScriptedRowLoader loader = new InMemoryRecStores.ScriptedRowLoader()
                .put("source", List.of(InMemoryRecStores.row("abc", "def")))
                .put("target", List.of(InMemoryRecStores.row("abc", "def")));
        InMemoryRecStores.MemoryRecRunRepository runs = new InMemoryRecStores.MemoryRecRunRepository();
        InMemoryRecStores.MemoryRecRecordRepository records = new InMemoryRecStores.MemoryRecRecordRepository();

        new DatasetRecService(loader, runs, records)
                .reconcile(InMemoryRecStores.dataset("matched", List.of(), List.of()))
                .block();

        assertEquals(0, records.inserted.size());
        assertEquals(1, runs.lastSummary.matched());
    }

    @Test
    void reconcilesMismatchedHashes() {
        InMemoryRecStores.ScriptedRowLoader loader = new InMemoryRecStores.ScriptedRowLoader()
                .put("source", List.of(InMemoryRecStores.row("abc", "left")))
                .put("target", List.of(InMemoryRecStores.row("abc", "right")));
        InMemoryRecStores.MemoryRecRunRepository runs = new InMemoryRecStores.MemoryRecRunRepository();
        InMemoryRecStores.MemoryRecRecordRepository records = new InMemoryRecStores.MemoryRecRecordRepository();

        new DatasetRecService(loader, runs, records)
                .reconcile(InMemoryRecStores.dataset("mismatch", List.of(), List.of()))
                .block();

        assertEquals(com.mms.data.recon.recrun.RecRecordRepository.RecStatus.MISMATCHED, records.inserted.get(0).status());
        assertEquals(1, runs.lastSummary.mismatched());
    }

    @Test
    void countsModePersistsNoRecordDetails() {
        InMemoryRecStores.ScriptedRowLoader loader = new InMemoryRecStores.ScriptedRowLoader()
                .put("source", List.of(InMemoryRecStores.row("abc", "left")))
                .put("target", List.of(InMemoryRecStores.row("abc", "right")));
        InMemoryRecStores.MemoryRecRunRepository runs = new InMemoryRecStores.MemoryRecRunRepository();
        InMemoryRecStores.MemoryRecRecordRepository records = new InMemoryRecStores.MemoryRecRecordRepository();
        DatasetConfiguration dataset = InMemoryRecStores.dataset("counts", List.of(), List.of());
        dataset.getRecon().setMode(ReconMode.COUNTS);

        new DatasetRecService(loader, runs, records).reconcile(dataset).block();

        assertEquals(0, records.inserted.size());
        assertEquals(1, runs.lastSummary.mismatched());
        assertTrue(runs.find(1L).active());
        assertEquals(ReconMode.COUNTS.name(), runs.find(1L).reconMode());
    }

    @Test
    void fieldDetailsRecordsConditionFieldMismatches() {
        InMemoryRecStores.ScriptedRowLoader loader = new InMemoryRecStores.ScriptedRowLoader()
                .put("source", List.of(InMemoryRecStores.row("k", "Acme", "ACTIVE")))
                .put("target", List.of(InMemoryRecStores.row("k", "Acme", "CLOSED")));
        InMemoryRecStores.MemoryRecRunRepository runs = new InMemoryRecStores.MemoryRecRunRepository();
        InMemoryRecStores.MemoryRecRecordRepository records = new InMemoryRecStores.MemoryRecRecordRepository();
        DatasetConfiguration dataset = InMemoryRecStores.dataset("fields", List.of(), List.of());
        dataset.getSource().setFields(List.of("party_name", "status"));
        dataset.getTarget().setFields(List.of("party_name", "status"));
        dataset.getRecon().setMode(ReconMode.FIELD_DETAILS);
        dataset.getRecon().setConditionFields(List.of("party_name", "status"));

        new DatasetRecService(loader, runs, records).reconcile(dataset).block();

        assertEquals(1, records.inserted.size());
        assertEquals(
                com.mms.data.recon.recrun.RecRecordRepository.RecStatus.MISMATCHED,
                records.inserted.get(0).status()
        );
        assertEquals("{\"party_name\":\"MATCHED\",\"status\":\"MISMATCHED\"}", records.inserted.get(0).fieldDiffs());
    }

    @Test
    void mismatchDetailsCanBeConditionalOnSelectedFields() {
        InMemoryRecStores.ScriptedRowLoader loader = new InMemoryRecStores.ScriptedRowLoader()
                .put("source", List.of(InMemoryRecStores.row("k", "Acme", "ACTIVE")))
                .put("target", List.of(InMemoryRecStores.row("k", "Acme", "CLOSED")));
        InMemoryRecStores.MemoryRecRunRepository runs = new InMemoryRecStores.MemoryRecRunRepository();
        InMemoryRecStores.MemoryRecRecordRepository records = new InMemoryRecStores.MemoryRecRecordRepository();
        DatasetConfiguration dataset = InMemoryRecStores.dataset("conditional", List.of(), List.of());
        dataset.getSource().setFields(List.of("party_name", "status"));
        dataset.getTarget().setFields(List.of("party_name", "status"));
        dataset.getRecon().setMode(ReconMode.MISMATCH_DETAILS);
        dataset.getRecon().setConditionFields(List.of("party_name"));

        new DatasetRecService(loader, runs, records).reconcile(dataset).block();

        assertEquals(0, records.inserted.size());
        assertEquals(1, runs.lastSummary.mismatched());
    }

    @Test
    void failedLoadMarksRunFailed() {
        RuntimeException root = new IllegalArgumentException("Could not connect to database");
        InMemoryRecStores.ScriptedRowLoader loader = new InMemoryRecStores.ScriptedRowLoader().fail(root);
        InMemoryRecStores.MemoryRecRunRepository runs = new InMemoryRecStores.MemoryRecRunRepository();
        InMemoryRecStores.MemoryRecRecordRepository records = new InMemoryRecStores.MemoryRecRecordRepository();
        DatasetRecService service = new DatasetRecService(loader, runs, records);

        assertThrows(RuntimeException.class, () ->
                service.reconcile(InMemoryRecStores.dataset("fail", List.of(), List.of())).block());
        assertEquals(1, runs.failures.size());
        assertEquals("Could not connect to database", runs.failures.get(1L));
    }

    @Test
    void storesOptionalQueriesAndReconOptionsOnTheRun() {
        InMemoryRecStores.ScriptedRowLoader loader = new InMemoryRecStores.ScriptedRowLoader()
                .put("source", List.of(InMemoryRecStores.row("k", "v")))
                .put("target", List.of(InMemoryRecStores.row("k", "v")));
        InMemoryRecStores.MemoryRecRunRepository runs = new InMemoryRecStores.MemoryRecRunRepository();
        InMemoryRecStores.MemoryRecRecordRepository records = new InMemoryRecStores.MemoryRecRecordRepository();
        DatasetConfiguration dataset = InMemoryRecStores.dataset("query-run", List.of(), List.of());
        dataset.getSource().setQuery("SELECT party_id AS \"MigrationKey\", party_name FROM landing.party");
        dataset.getTarget().setQuery("{ \"status\": \"ACTIVE\" }");
        dataset.getTarget().setCollection("party");
        dataset.getRecon().setMode(ReconMode.FIELD_DETAILS);
        dataset.getRecon().setConditionFields(List.of("party_name", "status"));

        new DatasetRecService(loader, runs, records).reconcile(dataset).block();

        RecRunRepository.RunView stored = runs.find(1L);
        assertEquals("SELECT party_id AS \"MigrationKey\", party_name FROM landing.party", stored.sourceQuery());
        assertEquals("{ \"status\": \"ACTIVE\" }", stored.targetQuery());
        assertEquals(List.of("party_name", "status"), stored.conditionFields());
        assertEquals(ReconMode.FIELD_DETAILS.name(), stored.reconMode());
        assertEquals(1, runs.lastSummary.matched());
    }
}
