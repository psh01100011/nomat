package com.dogdog.nomat.domain.emailverification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dogdog.nomat.domain.emailverification.config.EmailVerificationProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.MessageRejectedException;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

class SesEmailVerificationMailSenderTest {

    private final SesV2Client client = mock(SesV2Client.class);
    private final EmailVerificationProperties properties = properties();
    private final SesEmailVerificationMailSender sender = new SesEmailVerificationMailSender(client, properties);

    @Test
    void sendsKoreanUtf8TextAndHtmlToExactlyOneRecipient() {
        sender.sendVerificationCode("recipient@example.com", "123456");

        ArgumentCaptor<SendEmailRequest> captured = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(client).sendEmail(captured.capture());
        SendEmailRequest request = captured.getValue();
        assertThat(request.fromEmailAddress()).isEqualTo("no-reply@nomat.cloud");
        assertThat(request.destination().toAddresses()).containsExactly("recipient@example.com");
        assertThat(request.destination().ccAddresses()).isEmpty();
        assertThat(request.destination().bccAddresses()).isEmpty();
        assertThat(request.content().simple().subject().charset()).isEqualTo("UTF-8");
        assertThat(request.content().simple().subject().data()).isEqualTo("[NOMAT] 이메일 인증번호");
        assertThat(request.content().simple().body().text().charset()).isEqualTo("UTF-8");
        assertThat(request.content().simple().body().text().data()).contains("123456", "10분");
        assertThat(request.content().simple().body().html().charset()).isEqualTo("UTF-8");
        assertThat(request.content().simple().body().html().data()).contains("123456", "10분", "lang=\"ko\"");
    }

    @Test
    void translatesSesRejectionAndCredentialFailuresToExistingDeliveryFailure() {
        for (RuntimeException failure : new RuntimeException[] {
                MessageRejectedException.builder().message("rejected").build(),
                SdkClientException.create("credentials unavailable")
        }) {
            when(client.sendEmail(any(SendEmailRequest.class))).thenThrow(failure);
            assertThatThrownBy(() -> sender.sendVerificationCode("recipient@example.com", "123456"))
                    .isInstanceOf(EmailVerificationMailException.class)
                    .hasCause(failure);
        }
    }

    @Test
    void selectsExactlyOneDeliveryImplementationForSesAndSmtp() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(SesEmailVerificationMailSender.class, SmtpEmailVerificationMailSender.class)
                .withBean(EmailVerificationProperties.class, () -> properties)
                .withBean(SesV2Client.class, () -> client)
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class));
        runner.withPropertyValues("app.email-verification.delivery-mode=ses").run(context -> {
            assertThat(context).hasSingleBean(EmailVerificationMailSender.class);
            assertThat(context.getBean(EmailVerificationMailSender.class)).isInstanceOf(SesEmailVerificationMailSender.class);
        });
        runner.withPropertyValues("app.email-verification.delivery-mode=smtp").run(context -> {
            assertThat(context).hasSingleBean(EmailVerificationMailSender.class);
            assertThat(context.getBean(EmailVerificationMailSender.class)).isInstanceOf(SmtpEmailVerificationMailSender.class);
        });
    }

    private static EmailVerificationProperties properties() {
        EmailVerificationProperties properties = new EmailVerificationProperties();
        properties.setMailFrom("no-reply@nomat.cloud");
        properties.setSesRegion("ap-northeast-2");
        return properties;
    }
}
