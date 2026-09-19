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
            helper.setSubject("[NOMAT] 이메일 인증번호");
            helper.setText(plainText(code), htmlText(code));
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new EmailVerificationMailException(exception);
        }
    }

    private String plainText(String code) {
        return "NOMAT 이메일 인증번호\n\n"
                + code
                + "\n\n인증번호를 복사해 화면에 입력해주세요. "
                + properties.getCodeValidityMinutes()
                + "분 동안 사용할 수 있습니다.";
    }

    private String htmlText(String code) {
        return """
                <!doctype html>
                <html lang="ko">
                  <body style="margin:0;background:#f5f7fa;font-family:Arial,sans-serif;color:#172033;">
                    <div style="max-width:520px;margin:0 auto;padding:32px 20px;">
                      <div style="background:#ffffff;border:1px solid #dfe4ea;padding:28px;">
                        <h1 style="margin:0 0 12px;font-size:22px;">NOMAT 이메일 인증</h1>
                        <p style="margin:0 0 22px;line-height:1.6;color:#596273;">
                          아래 인증번호를 복사해 인증 화면에 입력해주세요.
                        </p>
                        <div style="user-select:all;-webkit-user-select:all;border:1px solid #b8c1cc;
                                    background:#f8fafc;padding:18px;text-align:center;font-size:32px;
                                    font-weight:700;letter-spacing:8px;color:#111827;">
                          %s
                        </div>
                        <p style="margin:20px 0 0;font-size:13px;line-height:1.6;color:#788292;">
                          이 번호는 %d분 동안 사용할 수 있습니다. 본인이 요청하지 않았다면 이 메일을 무시해주세요.
                        </p>
                      </div>
                    </div>
                  </body>
                </html>
                """.formatted(code, properties.getCodeValidityMinutes());
    }
}
