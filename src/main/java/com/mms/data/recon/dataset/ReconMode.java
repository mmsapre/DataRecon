package com.mms.data.recon.dataset;

/**
 * How much a run stores besides match/mismatch counts.
 */
public enum ReconMode {
    /** Counts only: matched, mismatched, source-only, target-only. No per-key rows. */
    COUNTS,
    /** Per-key details for mismatches (and source/target only). Optional condition fields filter which mismatches are stored. */
    MISMATCH_DETAILS,
    /** Mismatch details plus which condition fields differed (hashes only, not business values). */
    FIELD_DETAILS
}
