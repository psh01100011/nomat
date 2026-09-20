package com.dogdog.nomat.domain.emailverification.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.email-verification")
public class EmailVerificationProperties {

    @NotBlank
    private String deliveryMode = "smtp";

    @NotBlank
    @Email
    private String mailFrom;

    @Positive
    private long codeValidityMinutes = 10;

    @Positive
    private long completionTokenValidityMinutes = 30;

    @Positive
    private int maxVerificationAttempts = 5;

    @Positive
    private int maxSendsPerEmailHour = 10;

    @Positive
    private int maxSendsPerIpHour = 20;
}
