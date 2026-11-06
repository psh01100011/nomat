package com.dogdog.nomat.global.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dogdog.nomat.domain.map.config.AudioProcessingProperties;
import com.dogdog.nomat.domain.room.config.RoomWebSocketProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(MockitoExtension.class)
class ProductionEnvironmentValidatorTest {

    @Mock
    private ExternalCommandAvailabilityChecker commandAvailabilityChecker;

    private MockEnvironment environment;
    private RoomWebSocketProperties webSocketProperties;
    private AudioProcessingProperties audioProcessingProperties;

    @BeforeEach
    void setUp() {
        environment = validEnvironment();
        webSocketProperties = new RoomWebSocketProperties();
        webSocketProperties.setAllowedOrigins(List.of("https://nomat.example.com"));
        audioProcessingProperties = new AudioProcessingProperties();
    }

    @Test
    void acceptsCompleteApiServerConfigurationWithoutCheckingAudioTools() {
        ProductionEnvironmentValidator validator = validator();

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
        verify(commandAvailabilityChecker, never()).verifyAudioTools(audioProcessingProperties);
    }

    @Test
    void rejectsLocalJwtSecretAndMissingAdminKey() {
        environment.setProperty("app.auth.jwt.secret", ProductionEnvironmentValidator.LOCAL_JWT_SECRET);
        environment.setProperty("app.admin.api-key", "");

        assertThatThrownBy(validator()::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local development secret")
                .hasMessageContaining("app.admin.api-key must be configured");
    }

    @Test
    void rejectsInsecureOrWildcardWebSocketOrigins() {
        webSocketProperties.setAllowedOrigins(List.of("http://nomat.example.com", "https://*.example.com"));

        assertThatThrownBy(validator()::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS origin")
                .hasMessageContaining("must not contain wildcards");
    }

    @Test
    void checksExternalToolsWhenAudioWorkerIsEnabled() {
        audioProcessingProperties.setEnabled(true);

        validator().afterPropertiesSet();

        verify(commandAvailabilityChecker).verifyAudioTools(audioProcessingProperties);
    }

    @Test
    void rejectsSmtpOrLogDeliveryInProduction() {
        for (String mode : List.of("smtp", "log")) {
            environment.setProperty("app.email-verification.delivery-mode", mode);
            assertThatThrownBy(validator()::afterPropertiesSet)
                    .hasMessageContaining("delivery-mode must be ses");
        }
    }

    @Test
    void requiresSesRegionWithoutLeakingSecrets() {
        environment.setProperty("app.email-verification.ses-region", "");
        assertThatThrownBy(validator()::afterPropertiesSet)
                .hasMessageContaining("ses-region must be configured")
                .hasMessageNotContaining("database-secret");
    }

    private ProductionEnvironmentValidator validator() {
        return new ProductionEnvironmentValidator(
                environment,
                webSocketProperties,
                audioProcessingProperties,
                commandAvailabilityChecker
        );
    }

    private MockEnvironment validEnvironment() {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://db:3306/nomat")
                .withProperty("spring.datasource.username", "nomat")
                .withProperty("spring.datasource.password", "database-secret")
                .withProperty("spring.data.redis.url", "redis://redis:6379")
                .withProperty("app.email-verification.delivery-mode", "ses")
                .withProperty("app.email-verification.ses-region", "ap-northeast-2")
                .withProperty("app.email-verification.mail-from", "sender@example.com")
                .withProperty("app.asset.s3.bucket", "nomat-assets")
                .withProperty("app.asset.s3.region", "ap-northeast-2")
                .withProperty("app.asset.s3.public-base-url", "https://assets.example.com")
                .withProperty("app.http.trusted-proxy-cidrs", "10.0.0.0/16")
                .withProperty("app.auth.jwt.secret", "production-jwt-secret-with-at-least-32-bytes")
                .withProperty("app.admin.api-key", "production-admin-key-with-at-least-32-bytes")
                .withProperty("app.auth.cookie.secure", "true")
                .withProperty("app.auth.cookie.same-site", "Lax");
    }
}
