package com.studentmanagement.backend.util;

import java.util.Locale;

/** One canonical form for email identities: trimmed and lower-cased. */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
