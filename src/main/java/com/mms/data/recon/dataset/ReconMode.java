package com.mms.data.recon.dataset;

/**
 * How much a run stores after compare.
 * Both modes use the same detail query shape (MigrationKey + fields).
 * {@link #COUNTS} hashes rows in-memory; detail modes stream into DuckDB {@code EXCEPT ALL}.
 */
public enum ReconMode {
    /** Field query → hash compare; store counts only (no per-key rows, no DuckDB). */
    COUNTS,
    /**
     * Field query → DuckDB EXCEPT; persist per-key rows for mismatches (and source/target only).
     * Optional condition fields filter which mismatches are stored. Includes row payloads.
     */
    MISMATCH_DETAILS,
    /**
     * Field query → DuckDB EXCEPT; persist mismatch details plus which condition fields differed.
     * Payloads are stored for API retrieval.
     */
    FIELD_DETAILS
}
