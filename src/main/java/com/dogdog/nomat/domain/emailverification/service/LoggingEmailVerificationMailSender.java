package com.dogdog.nomat.domain.emailverification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(name = "app.email-verification.delivery-mode", havingValue = "log")
public class LoggingEmailVerificationMailSender implements EmailVerificationMailSender {

    @Override
    public void sendVerificationCode(String email, String code) {
        log.info("event=local_email_verification_code email={} code={}", email, code);
    }
}
