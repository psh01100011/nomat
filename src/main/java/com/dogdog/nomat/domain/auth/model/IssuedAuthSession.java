package com.dogdog.nomat.domain.auth.model;

public record IssuedAuthSession<T>(
        T response,
        String refreshToken
) {
}
