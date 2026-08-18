package com.mms.data.recon.config;

import java.time.Instant;

/**
 * Audit + version metadata for catalog entities (datasource, domain, profile).
 * Updates create a new version and mark the previous row inactive.
 */
public record CatalogAudit(
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        boolean active,
        int version) {

    public static CatalogAudit create(String actor) {
        Instant now = Instant.now();
        String by = blankToApp(actor);
        return new CatalogAudit(now, by, now, by, true, 1);
    }

    public CatalogAudit nextVersion(String actor) {
        return new CatalogAudit(
                createdAt,
                createdBy,
                Instant.now(),
                blankToApp(actor),
                true,
                version + 1
        );
    }

    public CatalogAudit deactivate(String actor) {
        return new CatalogAudit(
                createdAt,
                createdBy,
                Instant.now(),
                blankToApp(actor),
                false,
                version
        );
    }

    private static String blankToApp(String actor) {
        return actor == null || actor.isBlank() ? "data-recon" : actor.trim();
    }
}
