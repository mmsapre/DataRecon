package com.mms.data.recon.dataset;

import com.mms.data.recon.recrun.RecRecordRepository;
import com.mms.data.recon.recrun.RecRunRepository;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryRecStores {

    private InMemoryRecStores() {}

    public static DatasetConfiguration dataset(
            String id,
            List<DataLoadDefinition.RawRow> sourceRows,
            List<DataLoadDefinition.RawRow> targetRows) {
        DataLoadDefinition source = new DataLoadDefinition();
        source.setDatasourceRef("source");
        source.setType(DatasourceType.postgres);
        DataLoadDefinition target = new DataLoadDefinition();
        target.setDatasourceRef("target");
        target.setType(DatasourceType.postgres);

        DatasetConfiguration dataset = new DatasetConfiguration();
        dataset.setId(id);
        dataset.setDomainId(id);
        dataset.setSource(source);
        dataset.setTarget(target);
        dataset.setBatchSize(100);
        dataset.setHashingStrategy(HashingStrategy.TypeLenient);
        dataset.initialize();
        return dataset;
    }

    public static DatasetConfiguration profile(
            String domainId,
            String profileId,
            String sourceRef,
            String targetRef) {
        DataLoadDefinition source = new DataLoadDefinition();
        source.setDatasourceRef(sourceRef);
        source.setType(DatasourceType.postgres);
        DataLoadDefinition target = new DataLoadDefinition();
        target.setDatasourceRef(targetRef);
        target.setType(DatasourceType.postgres);

        DatasetConfiguration dataset = new DatasetConfiguration();
        dataset.setDomainId(domainId);
        dataset.setProfileId(profileId);
        dataset.setId(DatasetConfiguration.qualifiedId(domainId, profileId));
        dataset.setSource(source);
        dataset.setTarget(target);
        dataset.setBatchSize(100);
        dataset.setHashingStrategy(HashingStrategy.TypeLenient);
        dataset.initialize();
        return dataset;
    }

    public static DataLoadDefinition.RawRow row(String key, Object... comparable) {
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        columns.add(DataLoadDefinition.MIGRATION_KEY_COLUMN_NAME);
        values.add(key);
        for (int i = 0; i < comparable.length; i++) {
            columns.add("c" + i);
            values.add(comparable[i]);
        }
        return new DataLoadDefinition.RawRow(columns, values);
    }

    public static final class ScriptedRowLoader implements RowLoader {
        private final Map<String, List<DataLoadDefinition.RawRow>> rows = new LinkedHashMap<>();
        private RuntimeException error;

        public ScriptedRowLoader put(String datasourceRef, List<DataLoadDefinition.RawRow> values) {
            rows.put(datasourceRef, values);
            return this;
        }

        public ScriptedRowLoader fail(RuntimeException error) {
            this.error = error;
            return this;
        }

        @Override
        public Flux<DataLoadDefinition.RawRow> load(DataLoadDefinition definition, int batchSize) {
            if (error != null) {
                return Flux.error(error);
            }
            return Flux.fromIterable(rows.getOrDefault(definition.getDatasourceRef(), List.of()));
        }
    }

    public static final class MemoryRecRunRepository extends RecRunRepository {
        private final AtomicLong nextId = new AtomicLong(1);
        final Map<Long, RecRunRepository.RunView> runs = new LinkedHashMap<>();
        final Map<Long, String> failures = new LinkedHashMap<>();
        RecRunRepository.RunSummary lastSummary;

        public MemoryRecRunRepository() {
            super(null);
        }

        @Override
        public long create(String datasetId) {
            return create(datasetId, null, null, null);
        }

        @Override
        public long create(String domainId, String profileId, Long domainRunId) {
            return create(domainId, profileId, domainRunId, null);
        }

        @Override
        public long create(String domainId, String profileId, Long domainRunId, com.mms.data.recon.dataset.ReconMode mode) {
            return create(domainId, profileId, domainRunId, mode, null, null, null);
        }

        @Override
        public long create(
                String domainId,
                String profileId,
                Long domainRunId,
                com.mms.data.recon.dataset.ReconMode mode,
                String sourceQuery,
                String targetQuery,
                List<String> conditionFields) {
            long id = nextId.getAndIncrement();
            String datasetId = profileId == null || profileId.isBlank()
                    ? domainId
                    : DatasetConfiguration.qualifiedId(domainId, profileId);
            runs.put(id, new RecRunRepository.RunView(
                    id, datasetId, domainId, profileId, domainRunId,
                    "RUNNING", Instant.now(), null, 0, 0, 0, 0, 0, 0, null,
                    false, mode == null ? null : mode.name(),
                    sourceQuery, targetQuery,
                    conditionFields == null ? List.of() : List.copyOf(conditionFields)
            ));
            return id;
        }

        @Override
        public void complete(long id, RecRunRepository.RunSummary summary) {
            this.lastSummary = summary;
            RecRunRepository.RunView prev = runs.get(id);
            if (prev != null) {
                runs.put(id, new RecRunRepository.RunView(
                        prev.id(), prev.datasetId(), prev.domainId(), prev.profileId(), prev.domainRunId(),
                        "COMPLETED", prev.startedAt(), Instant.now(),
                        summary.sourceCount(), summary.targetCount(), summary.matched(),
                        summary.mismatched(), summary.sourceOnly(), summary.targetOnly(),
                        prev.errorMessage(), prev.active(), prev.reconMode(),
                        prev.sourceQuery(), prev.targetQuery(), prev.conditionFields()
                ));
            }
            activate(id);
        }

        @Override
        public void activate(long id) {
            RecRunRepository.RunView current = runs.get(id);
            if (current == null) {
                return;
            }
            runs.replaceAll((ignored, run) -> {
                if (run.id() != id
                        && java.util.Objects.equals(run.domainId(), current.domainId())
                        && java.util.Objects.equals(run.profileId(), current.profileId())
                        && run.active()) {
                    return new RecRunRepository.RunView(
                            run.id(), run.datasetId(), run.domainId(), run.profileId(), run.domainRunId(),
                            run.status(), run.startedAt(), run.completedAt(),
                            run.sourceCount(), run.targetCount(), run.matched(),
                            run.mismatched(), run.sourceOnly(), run.targetOnly(),
                            run.errorMessage(), false, run.reconMode(),
                            run.sourceQuery(), run.targetQuery(), run.conditionFields()
                    );
                }
                return run;
            });
            RecRunRepository.RunView latest = runs.get(id);
            runs.put(id, new RecRunRepository.RunView(
                    latest.id(), latest.datasetId(), latest.domainId(), latest.profileId(), latest.domainRunId(),
                    latest.status(), latest.startedAt(), latest.completedAt(),
                    latest.sourceCount(), latest.targetCount(), latest.matched(),
                    latest.mismatched(), latest.sourceOnly(), latest.targetOnly(),
                    latest.errorMessage(), true, latest.reconMode(),
                    latest.sourceQuery(), latest.targetQuery(), latest.conditionFields()
            ));
        }

        @Override
        public void fail(long id, Throwable error) {
            String message = error == null ? null : error.getMessage();
            failures.put(id, message);
            RecRunRepository.RunView prev = runs.get(id);
            if (prev != null) {
                runs.put(id, new RecRunRepository.RunView(
                        prev.id(), prev.datasetId(), prev.domainId(), prev.profileId(), prev.domainRunId(),
                        "FAILED", prev.startedAt(), Instant.now(),
                        prev.sourceCount(), prev.targetCount(), prev.matched(),
                        prev.mismatched(), prev.sourceOnly(), prev.targetOnly(),
                        message, false, prev.reconMode(),
                        prev.sourceQuery(), prev.targetQuery(), prev.conditionFields()
                ));
            }
        }

        @Override
        public List<RecRunRepository.RunView> listByProfile(String domainId, String profileId, Boolean active) {
            return runs.values().stream()
                    .filter(run -> domainId.equals(run.domainId()) && profileId.equals(run.profileId()))
                    .filter(run -> active == null || active == run.active())
                    .toList();
        }

        @Override
        public List<RecRunRepository.RunView> listByDomain(String domainId, Boolean active) {
            return runs.values().stream()
                    .filter(run -> domainId.equals(run.domainId()))
                    .filter(run -> active == null || active == run.active())
                    .toList();
        }

        @Override
        public List<RecRunRepository.RunView> list(String datasetId) {
            return runs.values().stream()
                    .filter(run -> datasetId == null || datasetId.equals(run.datasetId()))
                    .toList();
        }

        @Override
        public List<RecRunRepository.RunView> listByDomain(String domainId) {
            return runs.values().stream()
                    .filter(run -> domainId.equals(run.domainId()))
                    .toList();
        }

        @Override
        public List<RecRunRepository.RunView> listByProfile(String domainId, String profileId) {
            return runs.values().stream()
                    .filter(run -> domainId.equals(run.domainId()) && profileId.equals(run.profileId()))
                    .toList();
        }

        @Override
        public List<RecRunRepository.RunView> listByDomainRun(long domainRunId) {
            return runs.values().stream()
                    .filter(run -> run.id() == domainRunId || Long.valueOf(domainRunId).equals(run.domainRunId()))
                    .toList();
        }

        @Override
        public RecRunRepository.RunView find(long id) {
            return runs.get(id);
        }
    }

    public static final class MemoryRecRecordRepository extends RecRecordRepository {
        final List<RecRecordRepository.RecRecord> inserted = new ArrayList<>();

        public MemoryRecRecordRepository() {
            super(null);
        }

        @Override
        public void insertBatch(long runId, List<RecRecordRepository.RecRecord> records) {
            inserted.addAll(records);
        }

        @Override
        public List<RecRecordRepository.RecRecord> findByRun(long runId, String status) {
            if (status == null || status.isBlank()) {
                return List.copyOf(inserted);
            }
            return inserted.stream().filter(record -> record.status().name().equals(status)).toList();
        }
    }
}
