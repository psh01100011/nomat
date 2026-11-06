package com.dogdog.nomat.domain.emailverification.service;

final class EmailVerificationMailContent {

    static final String SUBJECT = "[NOMAT] 이메일 인증번호";

    private EmailVerificationMailContent() {}

    static String plainText(String code, long validityMinutes) {
        return "NOMAT 이메일 인증번호\n\n"
                + code
                + "\n\n인증번호를 복사해 화면에 입력해주세요. "
                + validityMinutes
                + "분 동안 사용할 수 있습니다.";
    }

    static String htmlText(String code, long validityMinutes) {
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
                """.formatted(code, validityMinutes);
    }
}
