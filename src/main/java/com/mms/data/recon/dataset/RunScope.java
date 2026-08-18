package com.mms.data.recon.dataset;

/**
 * Whether a profile run is a full extract/compare or incremental from the previous active run.
 */
public enum RunScope {
    /** Reload and compare everything; replace the active baseline. */
    FULL,
    /** Compare current extract; persist only result rows that changed vs the previous active run. */
    INCREMENTAL
}
