package com.mms.data.recon.dataset;

import com.mms.data.recon.config.RecConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainConfigurationTest {

    @Test
    void initializeRequiresAtLeastOneProfile() {
        DomainConfiguration domain = new DomainConfiguration();
        assertThrows(IllegalArgumentException.class, () -> domain.initialize("party", new RecConfiguration.Defaults()));
    }

    @Test
    void rejectsInvalidDomainOrProfileNames() {
        DomainConfiguration domain = new DomainConfiguration();
        domain.setProfiles(Map.of("pg-pg", profile()));
        assertThrows(IllegalArgumentException.class, () -> domain.initialize("party.v1", new RecConfiguration.Defaults()));

        DomainConfiguration invalidProfile = new DomainConfiguration();
        Map<String, DatasetConfiguration> profiles = new LinkedHashMap<>();
        profiles.put("pg.pg", profile());
        invalidProfile.setProfiles(profiles);
        assertThrows(IllegalArgumentException.class, () -> invalidProfile.initialize("party", new RecConfiguration.Defaults()));
    }

    @Test
    void initializesQualifiedProfileIds() {
        DomainConfiguration domain = new DomainConfiguration();
        Map<String, DatasetConfiguration> profiles = new LinkedHashMap<>();
        profiles.put("pg-pg", profile());
        profiles.put("pg-mongo", profile());
        domain.setProfiles(profiles);
        domain.initialize("party", new RecConfiguration.Defaults());

        assertEquals("party", domain.getId());
        assertEquals("party.pg-pg", domain.requireProfile("pg-pg").getId());
        assertEquals("party.pg-mongo", domain.requireProfile("pg-mongo").getId());
    }

    private static DatasetConfiguration profile() {
        DataLoadDefinition source = new DataLoadDefinition();
        source.setDatasourceRef("landing");
        DataLoadDefinition target = new DataLoadDefinition();
        target.setDatasourceRef("master");
        DatasetConfiguration configuration = new DatasetConfiguration();
        configuration.setSource(source);
        configuration.setTarget(target);
        return configuration;
    }
}
