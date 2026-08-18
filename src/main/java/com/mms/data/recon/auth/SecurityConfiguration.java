package com.mms.data.recon.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, AuthConfiguration auth) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults());

        if (!auth.enabled()) {
            http.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll());
        } else {
            http.authorizeExchange(exchanges -> exchanges
                            .pathMatchers("/health", "/health/**").permitAll()
                            .pathMatchers(
                                    "/swagger-ui.html",
                                    "/swagger-ui/**",
                                    "/v3/api-docs",
                                    "/v3/api-docs/**",
                                    "/webjars/**"
                            ).permitAll()
                            .pathMatchers(HttpMethod.OPTIONS).permitAll()
                            .anyExchange().authenticated())
                    .httpBasic(Customizer.withDefaults());
        }

        return http.build();
    }

    @Bean
    public ReactiveUserDetailsService userDetailsService(AuthConfiguration auth) {
        if (!auth.enabled()) {
            UserDetails anonymous = User.withUsername("anonymous")
                    .password("unused")
                    .roles("ANONYMOUS")
                    .build();
            return new MapReactiveUserDetailsService(anonymous);
        }
        UserDetails user = User.withUsername(auth.getUsername())
                .password(auth.getPassword())
                .roles("DATA_RECON_USER")
                .build();
        return new MapReactiveUserDetailsService(user);
    }

    @Bean
    @SuppressWarnings("deprecation")
    public PasswordEncoder passwordEncoder() {
        // Plaintext passwords match existing DATA_RECON_PASSWORD / YAML config contract.
        return NoOpPasswordEncoder.getInstance();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(Environment environment) {
        String origin = environment.getProperty(
                "DATA_RECON_CORS_ORIGIN",
                "http://localhost:5173"
        );
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
