package com.dogdog.nomat.domain.emailverification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.email-verification.delivery-mode", havingValue = "log")
public class LoggingEmailVerificationMailSender implements EmailVerificationMailSender {

    @Override
    public void sendVerificationCode(String email, String code) {
        log.info("Local email verification code: email={}, code={}", email, code);
    }
}
