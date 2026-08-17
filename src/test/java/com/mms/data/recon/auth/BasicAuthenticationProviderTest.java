package com.mms.data.recon.auth;

import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAuthenticationProviderTest {

    @Test
    void authenticatesValidCredentials() {
        AuthConfiguration configuration = new AuthConfiguration();
        configuration.setUsername("admin");
        configuration.setPassword("secret");
        BasicAuthenticationProvider provider = new BasicAuthenticationProvider(configuration);

        AuthenticationResponse response = Mono.from(provider.authenticate(
                null,
                new UsernamePasswordCredentials("admin", "secret")
        )).block();

        assertTrue(response.isAuthenticated());
        assertEquals("admin", response.getAuthentication().orElseThrow().getName());
    }

    @Test
    void rejectsInvalidCredentials() {
        AuthConfiguration configuration = new AuthConfiguration();
        configuration.setUsername("admin");
        configuration.setPassword("secret");
        BasicAuthenticationProvider provider = new BasicAuthenticationProvider(configuration);

        AuthenticationResponse response = Mono.from(provider.authenticate(
                null,
                new UsernamePasswordCredentials("unknown", "secret")
        )).block();

        assertTrue(!response.isAuthenticated());
    }

    @Test
    void allowsAnonymousWhenAuthDisabled() {
        BasicAuthenticationProvider provider = new BasicAuthenticationProvider(new AuthConfiguration());

        AuthenticationResponse response = Mono.from(provider.authenticate(
                null,
                new UsernamePasswordCredentials("anyone", "x")
        )).block();

        assertTrue(response.isAuthenticated());
        assertEquals("anonymous", response.getAuthentication().orElseThrow().getName());
    }

    @Test
    void authConfigurationEnabledWhenUsernameSet() {
        AuthConfiguration configuration = new AuthConfiguration();
        assertTrue(!configuration.enabled());
        configuration.setUsername("admin");
        assertTrue(configuration.enabled());
    }
}
