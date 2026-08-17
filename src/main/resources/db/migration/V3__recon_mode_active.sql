ALTER TABLE ${runTable}
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE ${runTable}
    ADD COLUMN IF NOT EXISTS recon_mode VARCHAR(32);

ALTER TABLE ${recordTable}
    ADD COLUMN IF NOT EXISTS field_diffs TEXT;

CREATE INDEX IF NOT EXISTS idx_rec_run_active
    ON ${runTable}(domain_id, profile_id, active, id DESC);

UPDATE ${runTable} r
   SET active = true
 WHERE r.status = 'COMPLETED'
   AND r.id IN (
        SELECT DISTINCT ON (COALESCE(domain_id, ''), COALESCE(profile_id, '')) id
          FROM ${runTable}
         WHERE status = 'COMPLETED'
         ORDER BY COALESCE(domain_id, ''), COALESCE(profile_id, ''), id DESC
   );
