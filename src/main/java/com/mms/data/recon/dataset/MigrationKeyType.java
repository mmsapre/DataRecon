package com.mms.data.recon.dataset;

/**
 * Supported ways to build the MigrationKey used to match source and target rows.
 */
public enum MigrationKeyType {
    /** One column. */
    SINGLE,
    /** Combination of two or more columns. */
    COMPOSITE,
    /** Caller-defined SQL expression or Mongo field path. */
    DEFINED
}
