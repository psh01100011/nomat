package com.dogdog.nomat.domain.emailverification.service;

import com.dogdog.nomat.domain.emailverification.config.EmailVerificationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@Component
@ConditionalOnProperty(name = "app.email-verification.delivery-mode", havingValue = "ses")
@RequiredArgsConstructor
public class SesEmailVerificationMailSender implements EmailVerificationMailSender {

    private final SesV2Client sesClient;
    private final EmailVerificationProperties properties;

    @Override
    public void sendVerificationCode(String email, String code) {
        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(properties.getMailFrom())
                .destination(Destination.builder().toAddresses(email).build())
                .content(EmailContent.builder().simple(Message.builder()
                        .subject(utf8(EmailVerificationMailContent.SUBJECT))
                        .body(Body.builder()
                                .text(utf8(EmailVerificationMailContent.plainText(code, properties.getCodeValidityMinutes())))
                                .html(utf8(EmailVerificationMailContent.htmlText(code, properties.getCodeValidityMinutes())))
                                .build())
                        .build()).build())
                .build();
        try {
            sesClient.sendEmail(request);
        } catch (SdkException exception) {
            throw new EmailVerificationMailException(exception);
        }
    }

    private Content utf8(String value) {
        return Content.builder().charset("UTF-8").data(value).build();
    }
}
