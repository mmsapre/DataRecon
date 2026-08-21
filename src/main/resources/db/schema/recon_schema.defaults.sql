-- External / DBA apply script (default table names; schema is a parameter).
-- Use when mms.recon.database.manage-schema=false (or DATA_RECON_DB_MANAGE_SCHEMA=false).
-- Substitute ${schema} before running, e.g. public:
--   sed 's/${schema}/public/g' recon_schema.defaults.sql | psql ...
-- Or set schema=public in your DDL tool. Table names below are the app defaults.

CREATE SCHEMA IF NOT EXISTS ${schema};

CREATE TABLE IF NOT EXISTS ${schema}.rec_run (
    id BIGSERIAL PRIMARY KEY,
    dataset_id VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    source_count BIGINT NOT NULL DEFAULT 0,
    target_count BIGINT NOT NULL DEFAULT 0,
    matched_count BIGINT NOT NULL DEFAULT 0,
    mismatched_count BIGINT NOT NULL DEFAULT 0,
    source_only_count BIGINT NOT NULL DEFAULT 0,
    target_only_count BIGINT NOT NULL DEFAULT 0,
    error_message TEXT,
    domain_id VARCHAR(255),
    profile_id VARCHAR(255),
    domain_run_id BIGINT,
    active BOOLEAN NOT NULL DEFAULT false,
    recon_mode VARCHAR(32),
    source_query TEXT,
    target_query TEXT,
    condition_fields TEXT,
    run_scope VARCHAR(32),
    baseline_run_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(128) NOT NULL DEFAULT 'data-recon'
);

CREATE INDEX IF NOT EXISTS idx_rec_run_dataset
    ON ${schema}.rec_run(dataset_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_rec_run_domain
    ON ${schema}.rec_run(domain_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_rec_run_profile
    ON ${schema}.rec_run(domain_id, profile_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_rec_run_domain_run
    ON ${schema}.rec_run(domain_run_id);

CREATE INDEX IF NOT EXISTS idx_rec_run_active
    ON ${schema}.rec_run(domain_id, profile_id, active, id DESC);

CREATE INDEX IF NOT EXISTS idx_rec_run_baseline
    ON ${schema}.rec_run(baseline_run_id);

ALTER TABLE ${schema}.rec_run ADD COLUMN IF NOT EXISTS domain_id VARCHAR(255);
ALTER TABLE ${schema}.rec_run ADD COLUMN IF NOT EXISTS profile_id VARCHAR(255);
ALTER TABLE ${schema}.rec_run ADD COLUMN IF NOT EXISTS domain_run_id BIGINT;
ALTER TABLE ${schema}.rec_run ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE ${schema}.rec_run ADD COLUMN IF NOT EXISTS recon_mode VARCHAR(32);
ALTER TABLE ${schema}.rec_run ADD COLUMN IF NOT EXISTS source_query TEXT;
ALTER TABLE ${schema}.rec_run ADD COLUMN IF NOT EXISTS target_query TEXT;
ALTER TABLE ${schema}.rec_run ADD COLUMN IF NOT EXISTS condition_fields TEXT;
ALTER TABLE ${schema}.rec_run ADD COLUMN IF NOT EXISTS run_scope VARCHAR(32);
ALTER TABLE ${schema}.rec_run ADD COLUMN IF NOT EXISTS baseline_run_id BIGINT;
ALTER TABLE ${schema}.rec_run ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE ${schema}.rec_run ADD COLUMN IF NOT EXISTS created_by VARCHAR(128) NOT NULL DEFAULT 'data-recon';

CREATE TABLE IF NOT EXISTS ${schema}.rec_record (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES ${schema}.rec_run(id) ON DELETE CASCADE,
    migration_key VARCHAR(1024) NOT NULL,
    source_hash VARCHAR(128),
    target_hash VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    field_diffs TEXT,
    source_payload TEXT,
    target_payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(128) NOT NULL DEFAULT 'data-recon'
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rec_record_run_key
    ON ${schema}.rec_record(run_id, migration_key);

CREATE INDEX IF NOT EXISTS idx_rec_record_status
    ON ${schema}.rec_record(run_id, status);

ALTER TABLE ${schema}.rec_record ADD COLUMN IF NOT EXISTS field_diffs TEXT;
ALTER TABLE ${schema}.rec_record ADD COLUMN IF NOT EXISTS source_payload TEXT;
ALTER TABLE ${schema}.rec_record ADD COLUMN IF NOT EXISTS target_payload TEXT;
ALTER TABLE ${schema}.rec_record ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE ${schema}.rec_record ADD COLUMN IF NOT EXISTS created_by VARCHAR(128) NOT NULL DEFAULT 'data-recon';

CREATE TABLE IF NOT EXISTS ${schema}.rec_datasource (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    type            VARCHAR(32) NOT NULL,
    tags_json       TEXT,
    config_json     TEXT NOT NULL,
    version         INT NOT NULL DEFAULT 1,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(128) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rec_datasource_active_name
    ON ${schema}.rec_datasource(name)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_rec_datasource_name_version
    ON ${schema}.rec_datasource(name, version DESC);

CREATE TABLE IF NOT EXISTS ${schema}.rec_domain (
    id              BIGSERIAL PRIMARY KEY,
    domain_id       VARCHAR(128) NOT NULL,
    tags_json       TEXT,
    config_json     TEXT NOT NULL,
    version         INT NOT NULL DEFAULT 1,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(128) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rec_domain_active_id
    ON ${schema}.rec_domain(domain_id)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_rec_domain_id_version
    ON ${schema}.rec_domain(domain_id, version DESC);

CREATE TABLE IF NOT EXISTS ${schema}.rec_profile (
    id              BIGSERIAL PRIMARY KEY,
    domain_id       VARCHAR(128) NOT NULL,
    profile_id      VARCHAR(128) NOT NULL,
    tags_json       TEXT,
    config_json     TEXT NOT NULL,
    version         INT NOT NULL DEFAULT 1,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(128) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rec_profile_active
    ON ${schema}.rec_profile(domain_id, profile_id)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_rec_profile_version
    ON ${schema}.rec_profile(domain_id, profile_id, version DESC);
