package com.mms.data.recon.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import com.mms.data.recon.dataset.DatasetConfiguration;
import com.mms.data.recon.dataset.DomainConfiguration;
import com.mms.data.recon.dataset.HashingStrategy;
import com.mms.data.recon.dataset.ReconMode;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(MmsRecon.PREFIX)
public class RecConfiguration {

    private Defaults defaults = new Defaults();
    private Map<String, DomainConfiguration> domains = new LinkedHashMap<>();

    public Defaults getDefaults() { return defaults; }
    public void setDefaults(Defaults defaults) { this.defaults = defaults; }

    public Map<String, DomainConfiguration> getDomains() { return domains; }

    public void setDomains(Map<String, DomainConfiguration> domains) {
        this.domains = domains == null ? new LinkedHashMap<>() : domains;
        this.domains.forEach((id, domain) -> domain.initialize(id, defaults));
    }

    public DomainConfiguration requireDomain(String domainId) {
        DomainConfiguration domain = domains.get(domainId);
        if (domain == null) {
            throw new IllegalArgumentException("Unknown domain: " + domainId);
        }
        return domain;
    }

    public DatasetConfiguration requireProfile(String domainId, String profileId) {
        return requireDomain(domainId).requireProfile(profileId);
    }

    public Collection<DatasetConfiguration> allProfiles() {
        return domains.values().stream()
                .flatMap(domain -> domain.getProfiles().values().stream())
                .toList();
    }

    public static class Defaults {
        private int batchSize = 1000;
        private int batchConcurrency = 5;
        private HashingStrategy hashingStrategy = HashingStrategy.TypeLenient;
        private Path queryFileBaseDir = Path.of("queries");
        private ReconMode reconMode = ReconMode.MISMATCH_DETAILS;

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

        public int getBatchConcurrency() { return batchConcurrency; }
        public void setBatchConcurrency(int batchConcurrency) { this.batchConcurrency = batchConcurrency; }

        public HashingStrategy getHashingStrategy() { return hashingStrategy; }
        public void setHashingStrategy(HashingStrategy hashingStrategy) { this.hashingStrategy = hashingStrategy; }

        public Path getQueryFileBaseDir() { return queryFileBaseDir; }
        public void setQueryFileBaseDir(Path queryFileBaseDir) { this.queryFileBaseDir = queryFileBaseDir; }

        public ReconMode getReconMode() { return reconMode; }
        public void setReconMode(ReconMode reconMode) { this.reconMode = reconMode; }
    }
}
