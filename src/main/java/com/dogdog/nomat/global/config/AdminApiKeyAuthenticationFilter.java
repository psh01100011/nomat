package com.dogdog.nomat.global.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AdminApiKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String ADMIN_API_KEY_HEADER = "X-Admin-Key";

    private final AdminApiKeyProperties properties;

    public AdminApiKeyAuthenticationFilter(AdminApiKeyProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/admin/") && !"/actuator/prometheus".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String configuredKey = properties.getApiKey();
        String suppliedKey = request.getHeader(ADMIN_API_KEY_HEADER);
        if (StringUtils.hasText(configuredKey)
                && StringUtils.hasText(suppliedKey)
                && keysMatch(configuredKey, suppliedKey)) {
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    "operations",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private boolean keysMatch(String configuredKey, String suppliedKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] expected = digest.digest(configuredKey.getBytes(StandardCharsets.UTF_8));
            byte[] actual = digest.digest(suppliedKey.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected, actual);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
