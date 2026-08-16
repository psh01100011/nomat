package com.dogdog.nomat.global.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

@Configuration
public class SecurityConfig {

    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String PASSWORD_VERIFICATION_TOKEN_TYPE = "password_verification";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            BearerTokenResolver bearerTokenResolver,
            @Qualifier("jwtDecoder") JwtDecoder jwtDecoder
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/signup").permitAll()
                        .requestMatchers("/auth/login").permitAll()
                        .requestMatchers("/auth/refresh").permitAll()
                        .requestMatchers("/users/login-id/availability").permitAll()
                        .requestMatchers("/users/nickname/availability").permitAll()
                        .requestMatchers(RegexRequestMatcher.regexMatcher(HttpMethod.GET, "^/users/\\d+$")).permitAll()
                        .requestMatchers(HttpMethod.GET, "/maps").permitAll()
                        .requestMatchers(RegexRequestMatcher.regexMatcher(HttpMethod.GET, "^/maps/\\d+$")).permitAll()
                        .requestMatchers(RegexRequestMatcher.regexMatcher(HttpMethod.GET, "^/maps/\\d+/comments$")).permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                )
                .exceptionHandling(exception -> exception.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write("{\"message\":\"invalid_token\",\"data\":null}");
                }))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder(@Value("${app.auth.jwt.secret}") String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey(secret)));
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${app.auth.jwt.secret}") String secret,
            @Value("${app.auth.jwt.issuer}") String issuer
    ) {
        return jwtDecoder(secret, issuer, ACCESS_TOKEN_TYPE);
    }

    @Bean
    public JwtDecoder refreshJwtDecoder(
            @Value("${app.auth.jwt.secret}") String secret,
            @Value("${app.auth.jwt.issuer}") String issuer
    ) {
        return jwtDecoder(secret, issuer, REFRESH_TOKEN_TYPE);
    }

    @Bean
    public JwtDecoder passwordVerificationJwtDecoder(
            @Value("${app.auth.jwt.secret}") String secret,
            @Value("${app.auth.jwt.issuer}") String issuer
    ) {
        return jwtDecoder(secret, issuer, PASSWORD_VERIFICATION_TOKEN_TYPE);
    }

    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> {
            if ("/auth/refresh".equals(request.getServletPath())) {
                return null;
            }

            return delegate.resolve(request);
        };
    }

    private SecretKey jwtSecretKey(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private JwtDecoder jwtDecoder(String secret, String issuer, String tokenType) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
                .withSecretKey(jwtSecretKey(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> validator =
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        tokenTypeValidator(tokenType)
                );
        jwtDecoder.setJwtValidator(validator);

        return jwtDecoder;
    }

    private OAuth2TokenValidator<Jwt> tokenTypeValidator(String tokenType) {
        return jwt -> {
            if (tokenType.equals(jwt.getClaimAsString("tokenType"))) {
                return OAuth2TokenValidatorResult.success();
            }

            OAuth2Error error = new OAuth2Error("invalid_token", "Invalid token type.", null);
            return OAuth2TokenValidatorResult.failure(error);
        };
    }
}
