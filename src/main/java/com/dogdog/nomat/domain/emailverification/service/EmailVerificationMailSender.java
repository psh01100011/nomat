package com.dogdog.nomat.domain.emailverification.service;

public interface EmailVerificationMailSender {

    void sendVerificationCode(String email, String code);
}
