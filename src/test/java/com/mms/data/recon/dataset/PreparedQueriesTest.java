package com.mms.data.recon.dataset;

import com.mms.data.recon.config.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PreparedQueriesTest {

    @Test
    void convertsJdbcPlaceholdersToPostgres() {
        assertEquals(
                "SELECT * FROM party WHERE status = $1 AND country_code = $2",
                PreparedQueries.toPostgresPlaceholders(
                        "SELECT * FROM party WHERE status = ? AND country_code = ?"
                )
        );
    }

    @Test
    void ignoresPlaceholdersInsideQuotes() {
        assertEquals(
                "SELECT '?' AS x, party_id FROM party WHERE status = $1",
                PreparedQueries.toPostgresPlaceholders(
                        "SELECT '?' AS x, party_id FROM party WHERE status = ?"
                )
        );
    }

    @Test
    void bindsMongoStringPlaceholders() {
        assertEquals(
                "{ \"status\": \"ACTIVE\", \"country_code\": \"US\" }",
                PreparedQueries.bindMongoFilter(
                        "{ \"status\": \"?\", \"country_code\": \"?\" }",
                        List.of("ACTIVE", "US")
                )
        );
    }

    @Test
    void bindsMongoBarePlaceholders() {
        assertEquals(
                "{ \"status\": \"ACTIVE\", \"limit\": 10 }",
                PreparedQueries.bindMongoFilter(
                        "{ \"status\": ?, \"limit\": ? }",
                        List.of("ACTIVE", 10)
                )
        );
    }

    @Test
    void rejectsMismatchedMongoParams() {
        assertThrows(
                ConfigurationException.class,
                () -> PreparedQueries.bindMongoFilter("{ \"status\": \"?\" }", List.of())
        );
        assertThrows(
                ConfigurationException.class,
                () -> PreparedQueries.bindMongoFilter("{}", List.of("ACTIVE"))
        );
    }

    @Test
    void requireParamCountValidates() {
        PreparedQueries.requireParamCount("q", 2, List.of("a", "b"));
        assertThrows(
                ConfigurationException.class,
                () -> PreparedQueries.requireParamCount("q", 1, List.of("a", "b"))
        );
    }
}
