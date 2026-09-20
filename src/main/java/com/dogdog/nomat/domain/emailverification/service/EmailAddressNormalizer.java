package com.dogdog.nomat.domain.emailverification.service;

import java.util.Locale;

public final class EmailAddressNormalizer {

    public static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    public static final String OPTIONAL_EMAIL_PATTERN = "^$|" + EMAIL_PATTERN;

    private EmailAddressNormalizer() {
    }

    public static String normalizeNullable(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String email) {
        return email != null && email.length() <= 254 && email.matches(EMAIL_PATTERN);
    }
}
