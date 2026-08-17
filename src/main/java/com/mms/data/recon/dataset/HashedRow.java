package com.mms.data.recon.dataset;

public record HashedRow(
        String migrationKey,
        String hash,
        String sourceHash,
        String targetHash) {

    public static HashedRow source(String key, String hash) {
        return new HashedRow(key, hash, hash, null);
    }

    public HashedRow withTarget(String targetHash) {
        return new HashedRow(migrationKey, hash, sourceHash, targetHash);
    }
}
