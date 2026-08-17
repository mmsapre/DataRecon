ALTER TABLE ${runTable}
    ADD COLUMN IF NOT EXISTS domain_id VARCHAR(255);

ALTER TABLE ${runTable}
    ADD COLUMN IF NOT EXISTS profile_id VARCHAR(255);

ALTER TABLE ${runTable}
    ADD COLUMN IF NOT EXISTS domain_run_id BIGINT;

UPDATE ${runTable}
   SET domain_id = CASE
         WHEN dataset_id LIKE '%.%' THEN split_part(dataset_id, '.', 1)
         ELSE dataset_id
       END,
       profile_id = CASE
         WHEN dataset_id LIKE '%.%' THEN substring(dataset_id FROM position('.' IN dataset_id) + 1)
         ELSE NULL
       END
 WHERE domain_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_rec_run_domain
    ON ${runTable}(domain_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_rec_run_profile
    ON ${runTable}(domain_id, profile_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_rec_run_domain_run
    ON ${runTable}(domain_run_id);
