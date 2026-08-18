package com.mms.data.recon.dataset;

import com.mms.data.recon.config.RecConfiguration;

import java.nio.file.Path;

public class DatasetConfiguration {

    private transient String id;
    private transient String domainId;
    private transient String profileId;

    private DataLoadDefinition source;
    private DataLoadDefinition target;
    private ProfileDatasources datasources = new ProfileDatasources();
    private MigrationKeySpec migrationKey;
    private ReconSettings recon = new ReconSettings();

    private Integer batchSize;
    private Integer batchConcurrency;
    private HashingStrategy hashingStrategy;
    private Path queryFileBaseDir;
    private String schedule;
    private java.util.List<String> tags = java.util.List.of();
    private transient com.mms.data.recon.config.CatalogAudit audit;

    public static String qualifiedId(String domainId, String profileId) {
        return domainId + "." + profileId;
    }

    public void applyDefaults(RecConfiguration.Defaults defaults) {
        applyDefaults(defaults, null);
    }

    public void applyDefaults(RecConfiguration.Defaults defaults, DomainConfiguration domain) {
        if (batchSize == null) {
            batchSize = domain != null && domain.getBatchSize() != null
                    ? domain.getBatchSize()
                    : defaults.getBatchSize();
        }
        if (batchConcurrency == null) {
            batchConcurrency = domain != null && domain.getBatchConcurrency() != null
                    ? domain.getBatchConcurrency()
                    : defaults.getBatchConcurrency();
        }
        if (hashingStrategy == null) {
            hashingStrategy = domain != null && domain.getHashingStrategy() != null
                    ? domain.getHashingStrategy()
                    : defaults.getHashingStrategy();
        }
        if (queryFileBaseDir == null) {
            queryFileBaseDir = domain != null && domain.getQueryFileBaseDir() != null
                    ? domain.getQueryFileBaseDir()
                    : defaults.getQueryFileBaseDir();
        }
        if (recon == null) {
            recon = new ReconSettings();
        }
        if (recon.getMode() == null) {
            ReconMode inherited = domain != null && domain.getRecon() != null
                    ? domain.getRecon().getMode()
                    : null;
            recon.setMode(inherited != null ? inherited : defaults.getReconMode());
        }
        if ((recon.getConditionFields() == null || recon.getConditionFields().isEmpty())
                && domain != null
                && domain.getRecon() != null
                && domain.getRecon().getConditionFields() != null
                && !domain.getRecon().getConditionFields().isEmpty()) {
            recon.setConditionFields(domain.getRecon().getConditionFields());
        }
        recon.normalize();
    }

    public void initialize() {
        if (source == null || target == null) {
            throw new IllegalArgumentException("Profile " + id + " requires source and target");
        }
        attachDatasources();
        MigrationKeySpec resolvedKey = resolveMigrationKey();
        source.initialize(id, DataLoadDefinition.Role.SOURCE, queryFileBaseDir);
        target.initialize(id, DataLoadDefinition.Role.TARGET, queryFileBaseDir);
        source.applyMigrationKey(resolvedKey);
        target.applyMigrationKey(resolvedKey);
    }

    private void attachDatasources() {
        if (datasources == null) {
            return;
        }
        source.applyDatasource(datasources.getSource());
        target.applyDatasource(datasources.getTarget());
    }

    private MigrationKeySpec resolveMigrationKey() {
        if (migrationKey != null) {
            migrationKey.initialize();
            return migrationKey;
        }
        MigrationKeySpec fromSource = source.getMigrationKey();
        MigrationKeySpec fromTarget = target.getMigrationKey();
        MigrationKeySpec resolved = fromSource != null ? fromSource : fromTarget;
        if (resolved != null) {
            resolved.initialize();
        }
        this.migrationKey = resolved;
        return resolved;
    }

    public String getDatasourceDescriptor() {
        String sourceRef = source == null ? null : source.getDatasourceRef();
        String targetRef = target == null ? null : target.getDatasourceRef();
        return "(" + sourceRef + " -> " + targetRef + ")";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDomainId() { return domainId; }
    public void setDomainId(String domainId) { this.domainId = domainId; }

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public DataLoadDefinition getSource() { return source; }
    public void setSource(DataLoadDefinition source) { this.source = source; }

    public DataLoadDefinition getTarget() { return target; }
    public void setTarget(DataLoadDefinition target) { this.target = target; }

    public ProfileDatasources getDatasources() { return datasources; }
    public void setDatasources(ProfileDatasources datasources) {
        this.datasources = datasources == null ? new ProfileDatasources() : datasources;
    }

    public MigrationKeySpec getMigrationKey() { return migrationKey; }

    public void setMigrationKey(MigrationKeySpec migrationKey) {
        this.migrationKey = migrationKey;
    }

    public void setMigrationKey(String column) {
        this.migrationKey = column == null || column.isBlank() ? null : MigrationKeySpec.single(column);
    }

    public ReconSettings getRecon() { return recon; }
    public void setRecon(ReconSettings recon) {
        this.recon = recon == null ? new ReconSettings() : recon;
    }

    public ReconSettings resolvedRecon() {
        if (recon == null) {
            recon = new ReconSettings();
        }
        recon.normalize();
        return recon;
    }

    public Integer getBatchSize() { return batchSize; }
    public void setBatchSize(Integer batchSize) { this.batchSize = batchSize; }

    public Integer getBatchConcurrency() { return batchConcurrency; }
    public void setBatchConcurrency(Integer batchConcurrency) { this.batchConcurrency = batchConcurrency; }

    public HashingStrategy getHashingStrategy() { return hashingStrategy; }
    public void setHashingStrategy(HashingStrategy hashingStrategy) { this.hashingStrategy = hashingStrategy; }

    public Path getQueryFileBaseDir() { return queryFileBaseDir; }
    public void setQueryFileBaseDir(Path queryFileBaseDir) { this.queryFileBaseDir = queryFileBaseDir; }

    public String getSchedule() { return schedule; }
    public void setSchedule(String schedule) { this.schedule = schedule; }

    public java.util.List<String> getTags() { return tags; }
    public void setTags(java.util.List<String> tags) {
        this.tags = com.mms.data.recon.config.Tags.copy(tags);
    }

    public com.mms.data.recon.config.CatalogAudit getAudit() { return audit; }
    public void setAudit(com.mms.data.recon.config.CatalogAudit audit) { this.audit = audit; }
}
