package com.mms.data.recon.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAuthenticationProviderTest {

    @Test
    void authenticatesValidCredentials() {
        AuthConfiguration configuration = new AuthConfiguration();
        configuration.setUsername("admin");
        configuration.setPassword("secret");
        ReactiveUserDetailsService users = new SecurityConfiguration().userDetailsService(configuration);

        var user = users.findByUsername("admin").block();
        assertNotNull(user);
        assertEquals("admin", user.getUsername());
        assertEquals("secret", user.getPassword());
    }

    @Test
    void rejectsInvalidCredentials() {
        AuthConfiguration configuration = new AuthConfiguration();
        configuration.setUsername("admin");
        configuration.setPassword("secret");
        ReactiveUserDetailsService users = new SecurityConfiguration().userDetailsService(configuration);

        assertEquals(null, users.findByUsername("unknown").block());
    }

    @Test
    void allowsAnonymousWhenAuthDisabled() {
        ReactiveUserDetailsService users = new SecurityConfiguration().userDetailsService(new AuthConfiguration());

        var user = users.findByUsername("anonymous").block();
        assertNotNull(user);
        assertEquals("anonymous", user.getUsername());
    }

    @Test
    void authConfigurationEnabledWhenUsernameSet() {
        AuthConfiguration configuration = new AuthConfiguration();
        assertTrue(!configuration.enabled());
        configuration.setUsername("admin");
        assertTrue(configuration.enabled());
    }
}
