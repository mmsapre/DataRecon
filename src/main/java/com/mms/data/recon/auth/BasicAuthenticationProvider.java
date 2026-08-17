package com.mms.data.recon.auth;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.security.authentication.*;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.List;

@Singleton
public class BasicAuthenticationProvider implements AuthenticationProvider<Object> {

    private final AuthConfiguration configuration;

    public BasicAuthenticationProvider(AuthConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public @NonNull Publisher<AuthenticationResponse> authenticate(
            @Nullable Object requestContext,
            @NonNull AuthenticationRequest<?, ?> request) {

        if (!configuration.enabled()) {
            return Mono.just(AuthenticationResponse.success("anonymous", List.of()));
        }

        Object identity = request.getIdentity();
        Object secret = request.getSecret();
        boolean ok =
                configuration.getUsername().equals(identity) &&
                configuration.getPassword().equals(secret);

        return Mono.just(ok
                ? AuthenticationResponse.success(String.valueOf(identity), List.of("DATA_RECON_USER"))
                : AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH));
    }
}
