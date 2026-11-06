package com.dogdog.nomat.domain.emailverification.service;

import com.dogdog.nomat.domain.emailverification.config.EmailVerificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.email-verification.delivery-mode",
        havingValue = "smtp",
        matchIfMissing = true
)
@RequiredArgsConstructor
public class SmtpEmailVerificationMailSender implements EmailVerificationMailSender {

    private final JavaMailSender mailSender;
    private final EmailVerificationProperties properties;

    @Override
    public void sendVerificationCode(String email, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.getMailFrom());
            helper.setTo(email);
            helper.setSubject(EmailVerificationMailContent.SUBJECT);
            helper.setText(
                    EmailVerificationMailContent.plainText(code, properties.getCodeValidityMinutes()),
                    EmailVerificationMailContent.htmlText(code, properties.getCodeValidityMinutes())
            );
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new EmailVerificationMailException(exception);
        }
    }
}
