package com.mms.data.recon.dataset;

import com.mms.data.recon.config.RecConfiguration;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A recon domain groups one or more source/target profiles (pairings).
 * Trigger and results can be requested for the whole domain or a single profile.
 */
public class DomainConfiguration {

    private static final String NAME_PATTERN = "[A-Za-z0-9][A-Za-z0-9_-]*";

    private transient String id;

    private Integer batchSize;
    private Integer batchConcurrency;
    private HashingStrategy hashingStrategy;
    private Path queryFileBaseDir;
    private ReconSettings recon = new ReconSettings();
    private java.util.List<String> tags = java.util.List.of();
    /** Default source/target datasource names inherited by profiles unless overridden. */
    private ProfileDatasources datasources = new ProfileDatasources();
    private Map<String, DatasetConfiguration> profiles = new LinkedHashMap<>();
    private transient com.mms.data.recon.config.CatalogAudit audit;

    public void initialize(String id, RecConfiguration.Defaults defaults) {
        initialize(id, defaults, true);
    }

    public void initialize(String id, RecConfiguration.Defaults defaults, boolean requireProfiles) {
        this.id = requireName("domain", id);
        if (profiles == null) {
            profiles = new LinkedHashMap<>();
        }
        if (requireProfiles && profiles.isEmpty()) {
            throw new IllegalArgumentException("Domain " + id + " requires at least one profile");
        }
        profiles.forEach((profileId, profile) -> initializeProfile(profileId, profile, defaults));
    }

    public DatasetConfiguration initializeProfile(
            String profileId,
            DatasetConfiguration profile,
            RecConfiguration.Defaults defaults) {
        return initializeProfile(profileId, profile, defaults, null);
    }

    public DatasetConfiguration initializeProfile(
            String profileId,
            DatasetConfiguration profile,
            RecConfiguration.Defaults defaults,
            com.mms.data.recon.config.DatasourceCatalog catalog) {
        requireName("profile", profileId);
        profile.setDomainId(this.id);
        profile.setProfileId(profileId);
        profile.setId(DatasetConfiguration.qualifiedId(this.id, profileId));
        profile.applyDefaults(defaults, this);
        profile.initialize(catalog);
        return profile;
    }

    public static String requireName(String field, String value) {
        if (value == null || value.isBlank() || !value.matches(NAME_PATTERN)) {
            throw new IllegalArgumentException(
                    field + " [" + value + "] must match " + NAME_PATTERN
            );
        }
        return value;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getBatchSize() { return batchSize; }
    public void setBatchSize(Integer batchSize) { this.batchSize = batchSize; }

    public Integer getBatchConcurrency() { return batchConcurrency; }
    public void setBatchConcurrency(Integer batchConcurrency) { this.batchConcurrency = batchConcurrency; }

    public HashingStrategy getHashingStrategy() { return hashingStrategy; }
    public void setHashingStrategy(HashingStrategy hashingStrategy) { this.hashingStrategy = hashingStrategy; }

    public Path getQueryFileBaseDir() { return queryFileBaseDir; }
    public void setQueryFileBaseDir(Path queryFileBaseDir) { this.queryFileBaseDir = queryFileBaseDir; }

    public ReconSettings getRecon() { return recon; }
    public void setRecon(ReconSettings recon) {
        this.recon = recon == null ? new ReconSettings() : recon;
    }

    public java.util.List<String> getTags() { return tags; }
    public void setTags(java.util.List<String> tags) {
        this.tags = com.mms.data.recon.config.Tags.copy(tags);
    }

    public ProfileDatasources getDatasources() { return datasources; }
    public void setDatasources(ProfileDatasources datasources) {
        this.datasources = datasources == null ? new ProfileDatasources() : datasources;
    }

    public com.mms.data.recon.config.CatalogAudit getAudit() { return audit; }
    public void setAudit(com.mms.data.recon.config.CatalogAudit audit) { this.audit = audit; }

    public Map<String, DatasetConfiguration> getProfiles() { return profiles; }

    public void setProfiles(Map<String, DatasetConfiguration> profiles) {
        this.profiles = profiles == null ? new LinkedHashMap<>() : profiles;
    }

    public DatasetConfiguration requireProfile(String profileId) {
        DatasetConfiguration profile = profiles.get(profileId);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown profile: " + id + "." + profileId);
        }
        return profile;
    }
}
