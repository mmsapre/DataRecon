package com.mms.data.recon.dataset;

import com.mms.data.recon.config.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationKeySpecTest {

    @Test
    void singleRequiresOneColumn() {
        assertEquals(MigrationKeyType.SINGLE, MigrationKeySpec.single("party_id").getType());
        assertEquals("party_id", MigrationKeySpec.single("party_id").sqlExpression(DatasourceType.postgres));

        MigrationKeySpec missing = new MigrationKeySpec();
        missing.setType(MigrationKeyType.SINGLE);
        assertThrows(ConfigurationException.class, missing::initialize);
    }

    @Test
    void compositeRequiresTwoOrMoreColumns() {
        MigrationKeySpec spec = MigrationKeySpec.composite(List.of("party_id", "country_code"));
        assertEquals(MigrationKeyType.COMPOSITE, spec.getType());
        assertEquals(
                "CAST(party_id AS TEXT) || '|' || CAST(country_code AS TEXT)",
                spec.sqlExpression(DatasourceType.postgres)
        );
        assertEquals(
                "CONCAT(CAST(party_id AS STRING), '|', CAST(country_code AS STRING))",
                spec.sqlExpression(DatasourceType.bigquery)
        );
        assertEquals(
                "CAST(party_id AS VARCHAR) || '|' || CAST(country_code AS VARCHAR)",
                spec.sqlExpression(DatasourceType.file)
        );
        assertEquals("P1|US", spec.compose(List.of("P1", "US")));

        MigrationKeySpec tooFew = new MigrationKeySpec();
        tooFew.setType(MigrationKeyType.COMPOSITE);
        tooFew.setColumns(List.of("party_id"));
        assertThrows(ConfigurationException.class, tooFew::initialize);
    }

    @Test
    void definedRequiresExpression() {
        MigrationKeySpec spec = MigrationKeySpec.defined("concat(party_id, '-', country_code)");
        assertEquals(MigrationKeyType.DEFINED, spec.getType());
        assertEquals(
                "concat(party_id, '-', country_code)",
                spec.sqlExpression(DatasourceType.postgres)
        );
        assertThrows(ConfigurationException.class, () -> MigrationKeySpec.defined("party_id; drop table x"));
    }

    @Test
    void infersTypeFromColumnsOrExpression() {
        MigrationKeySpec composite = new MigrationKeySpec();
        composite.setColumns(List.of("a", "b"));
        composite.initialize();
        assertEquals(MigrationKeyType.COMPOSITE, composite.getType());

        MigrationKeySpec defined = new MigrationKeySpec();
        defined.setExpression("a || b");
        defined.initialize();
        assertEquals(MigrationKeyType.DEFINED, defined.getType());
    }
}
