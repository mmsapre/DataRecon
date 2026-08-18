-- Incremental / full run metadata and detail payloads from DuckDB EXCEPT compares.
ALTER TABLE ${runTable}
    ADD COLUMN IF NOT EXISTS run_scope VARCHAR(32),
    ADD COLUMN IF NOT EXISTS baseline_run_id BIGINT;

ALTER TABLE ${recordTable}
    ADD COLUMN IF NOT EXISTS source_payload TEXT,
    ADD COLUMN IF NOT EXISTS target_payload TEXT;

CREATE INDEX IF NOT EXISTS idx_rec_run_baseline
    ON ${runTable}(baseline_run_id);
