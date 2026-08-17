package com.mms.data.recon.api;

import com.mms.data.recon.dataset.HashingStrategy;
import com.mms.data.recon.dataset.MigrationKeySpec;
import com.mms.data.recon.dataset.ProfileDatasources;

public class ProfileUpsertRequest {

    private String id;
    private String schedule;
    private HashingStrategy hashingStrategy;
    private Integer batchSize;
    private Integer batchConcurrency;
    private ProfileDatasources datasources;
    private MigrationKeySpec migrationKey;
    private ReconRunRequest recon;
    private SideRequest source;
    private SideRequest target;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSchedule() { return schedule; }
    public void setSchedule(String schedule) { this.schedule = schedule; }

    public HashingStrategy getHashingStrategy() { return hashingStrategy; }
    public void setHashingStrategy(HashingStrategy hashingStrategy) { this.hashingStrategy = hashingStrategy; }

    public Integer getBatchSize() { return batchSize; }
    public void setBatchSize(Integer batchSize) { this.batchSize = batchSize; }

    public Integer getBatchConcurrency() { return batchConcurrency; }
    public void setBatchConcurrency(Integer batchConcurrency) { this.batchConcurrency = batchConcurrency; }

    public ProfileDatasources getDatasources() { return datasources; }
    public void setDatasources(ProfileDatasources datasources) { this.datasources = datasources; }

    public MigrationKeySpec getMigrationKey() { return migrationKey; }
    public void setMigrationKey(MigrationKeySpec migrationKey) { this.migrationKey = migrationKey; }

    public ReconRunRequest getRecon() { return recon; }
    public void setRecon(ReconRunRequest recon) { this.recon = recon; }

    public SideRequest getSource() { return source; }
    public void setSource(SideRequest source) { this.source = source; }

    public SideRequest getTarget() { return target; }
    public void setTarget(SideRequest target) { this.target = target; }
}
