package com.dogdog.nomat.global.config;

import com.dogdog.nomat.domain.map.config.AudioProcessingProperties;
import com.dogdog.nomat.domain.room.config.RoomWebSocketProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("prod")
public class ProductionEnvironmentValidator implements InitializingBean {

    static final String LOCAL_JWT_SECRET = "nomat-local-development-secret-key-32";
    private static final int MIN_SECRET_BYTES = 32;

    private final Environment environment;
    private final RoomWebSocketProperties webSocketProperties;
    private final AudioProcessingProperties audioProcessingProperties;
    private final ExternalCommandAvailabilityChecker commandAvailabilityChecker;

    public ProductionEnvironmentValidator(
            Environment environment,
            RoomWebSocketProperties webSocketProperties,
            AudioProcessingProperties audioProcessingProperties,
            ExternalCommandAvailabilityChecker commandAvailabilityChecker
    ) {
        this.environment = environment;
        this.webSocketProperties = webSocketProperties;
        this.audioProcessingProperties = audioProcessingProperties;
        this.commandAvailabilityChecker = commandAvailabilityChecker;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> errors = new ArrayList<>();

        requireText(errors, "spring.datasource.url");
        requireText(errors, "spring.datasource.username");
        requireText(errors, "spring.datasource.password");
        requireText(errors, "spring.data.redis.url");
        requireText(errors, "spring.mail.host");
        requireText(errors, "spring.mail.username");
        requireText(errors, "spring.mail.password");
        requireText(errors, "app.email-verification.mail-from");
        requireText(errors, "app.asset.s3.bucket");
        requireText(errors, "app.asset.s3.region");
        requireText(errors, "app.asset.s3.public-base-url");
        requireText(errors, "app.http.trusted-proxy-cidrs");

        validateJwtSecret(errors);
        validateAdminApiKey(errors);
        validateCookiePolicy(errors);
        validatePublicUrls(errors);
        validateWebSocketOrigins(errors);

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid production configuration: " + String.join("; ", errors));
        }

        if (audioProcessingProperties.isEnabled()) {
            commandAvailabilityChecker.verifyAudioTools(audioProcessingProperties);
        }
    }

    private void validateJwtSecret(List<String> errors) {
        String secret = requireText(errors, "app.auth.jwt.secret");
        if (!StringUtils.hasText(secret)) {
            return;
        }
        if (LOCAL_JWT_SECRET.equals(secret)) {
            errors.add("app.auth.jwt.secret must not use the local development secret");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            errors.add("app.auth.jwt.secret must be at least 32 bytes");
        }
    }

    private void validateAdminApiKey(List<String> errors) {
        String apiKey = requireText(errors, "app.admin.api-key");
        if (StringUtils.hasText(apiKey)
                && apiKey.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            errors.add("app.admin.api-key must be at least 32 bytes");
        }
    }

    private void validateCookiePolicy(List<String> errors) {
        if (!environment.getProperty("app.auth.cookie.secure", Boolean.class, false)) {
            errors.add("app.auth.cookie.secure must be true");
        }
        String sameSite = environment.getProperty("app.auth.cookie.same-site", "");
        if (!"Lax".equalsIgnoreCase(sameSite)) {
            errors.add("app.auth.cookie.same-site must be Lax for the same-origin deployment");
        }
    }

    private void validatePublicUrls(List<String> errors) {
        validateHttpsUrl(errors, "app.asset.s3.public-base-url");
    }

    private void validateWebSocketOrigins(List<String> errors) {
        for (String origin : webSocketProperties.getAllowedOrigins()) {
            if (origin.contains("*")) {
                errors.add("WebSocket allowed origins must not contain wildcards");
                continue;
            }
            if (!isHttpsUrl(origin)) {
                errors.add("WebSocket allowed origin must be an HTTPS origin: " + origin);
            }
        }
    }

    private void validateHttpsUrl(List<String> errors, String propertyName) {
        String value = environment.getProperty(propertyName);
        if (StringUtils.hasText(value) && !isHttpsUrl(value)) {
            errors.add(propertyName + " must be an HTTPS URL");
        }
    }

    private boolean isHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && StringUtils.hasText(uri.getHost());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String requireText(List<String> errors, String propertyName) {
        String value = environment.getProperty(propertyName);
        if (!StringUtils.hasText(value)) {
            errors.add(propertyName + " must be configured");
        }
        return value;
    }
}
