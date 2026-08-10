package com.dogdog.nomat.domain.auth.token;

import com.dogdog.nomat.domain.user.entity.User;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

@Component
public class AuthTokenProvider {

    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String PASSWORD_VERIFICATION_TOKEN_TYPE = "password_verification";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder refreshJwtDecoder;
    private final JwtDecoder passwordVerificationJwtDecoder;

    @Value("${app.auth.jwt.issuer}")
    private String issuer;

    @Value("${app.auth.jwt.access-token-validity-seconds}")
    private long accessTokenValiditySeconds;

    @Value("${app.auth.jwt.refresh-token-validity-seconds}")
    private long refreshTokenValiditySeconds;

    @Value("${app.auth.jwt.password-verification-token-validity-seconds}")
    private long passwordVerificationTokenValiditySeconds;

    public AuthTokenProvider(
            JwtEncoder jwtEncoder,
            @Qualifier("refreshJwtDecoder") JwtDecoder refreshJwtDecoder,
            @Qualifier("passwordVerificationJwtDecoder") JwtDecoder passwordVerificationJwtDecoder
    ) {
        this.jwtEncoder = jwtEncoder;
        this.refreshJwtDecoder = refreshJwtDecoder;
        this.passwordVerificationJwtDecoder = passwordVerificationJwtDecoder;
    }

    public TokenPair issue(User user) {
        return new TokenPair(
                issueAccessToken(user),
                createToken(user, REFRESH_TOKEN_TYPE, refreshTokenValiditySeconds)
        );
    }

    public String issueAccessToken(User user) {
        return createToken(user, ACCESS_TOKEN_TYPE, accessTokenValiditySeconds);
    }

    public String issuePasswordVerificationToken(User user) {
        return createToken(user, PASSWORD_VERIFICATION_TOKEN_TYPE, passwordVerificationTokenValiditySeconds);
    }

    public Jwt decodeRefreshToken(String refreshToken) {
        return refreshJwtDecoder.decode(refreshToken);
    }

    public Jwt decodePasswordVerificationToken(String passwordVerificationToken) {
        return passwordVerificationJwtDecoder.decode(passwordVerificationToken);
    }

    private String createToken(User user, String tokenType, long validitySeconds) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(validitySeconds);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getLoginId())
                .claim("userId", user.getId())
                .claim("tokenType", tokenType)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
