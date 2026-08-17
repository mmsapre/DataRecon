package com.mms.data.recon.api;

import com.mms.data.recon.dataset.HashingStrategy;

public class DomainUpsertRequest {

    private String id;
    private String schedule;
    private HashingStrategy hashingStrategy;
    private Integer batchSize;
    private Integer batchConcurrency;
    private ReconRunRequest recon;

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

    public ReconRunRequest getRecon() { return recon; }
    public void setRecon(ReconRunRequest recon) { this.recon = recon; }
}
