package com.dogdog.nomat.global.config;

import java.security.Principal;

public record StompUserPrincipal(Long userId) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
