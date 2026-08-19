package com.mms.data.recon.api;

import com.mms.data.recon.dataset.HashingStrategy;
import com.mms.data.recon.dataset.ProfileDatasources;

import java.util.List;

public class DomainUpsertRequest {

    private String id;
    private HashingStrategy hashingStrategy;
    private Integer batchSize;
    private Integer batchConcurrency;
    private ReconRunRequest recon;
    private List<String> tags;
    /** Default named datasources for profiles in this domain (source/target). */
    private ProfileDatasources datasources;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public HashingStrategy getHashingStrategy() { return hashingStrategy; }
    public void setHashingStrategy(HashingStrategy hashingStrategy) { this.hashingStrategy = hashingStrategy; }

    public Integer getBatchSize() { return batchSize; }
    public void setBatchSize(Integer batchSize) { this.batchSize = batchSize; }

    public Integer getBatchConcurrency() { return batchConcurrency; }
    public void setBatchConcurrency(Integer batchConcurrency) { this.batchConcurrency = batchConcurrency; }

    public ReconRunRequest getRecon() { return recon; }
    public void setRecon(ReconRunRequest recon) { this.recon = recon; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public ProfileDatasources getDatasources() { return datasources; }
    public void setDatasources(ProfileDatasources datasources) { this.datasources = datasources; }
}
