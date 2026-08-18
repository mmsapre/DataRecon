package com.mms.data.recon.dataset;

/**
 * How much a run stores besides match/mismatch counts.
 * Detail modes stream source/target into DuckDB and compare with EXCEPT ALL.
 */
public enum ReconMode {
    /** Counts only (hash compare). No per-key rows. */
    COUNTS,
    /**
     * Per-key details for mismatches (and source/target only) via DuckDB EXCEPT ALL.
     * Optional condition fields filter which mismatches are stored. Includes row payloads.
     */
    MISMATCH_DETAILS,
    /**
     * DuckDB EXCEPT details plus which condition fields differed.
     * Payloads are stored for API retrieval.
     */
    FIELD_DETAILS
}
