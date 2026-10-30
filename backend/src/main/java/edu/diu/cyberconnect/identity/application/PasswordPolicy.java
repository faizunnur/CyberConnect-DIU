package edu.diu.cyberconnect.identity.application;

import edu.diu.cyberconnect.common.exception.BadRequestException;
import java.util.Locale;
import java.util.Set;

/** Length-based policy (NIST SP 800-63B) plus a short deny-list; no composition rules. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final Set<String> DENY = Set.of(
            "password1234", "123456789012", "qwertyuiop12", "cyberconnect", "cyberconnect1", "passwordpassword",
            "adminadmin12", "letmeinletmein", "welcome12345", "iloveyou1234");

    private PasswordPolicy() {
    }

    public static void validate(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new BadRequestException("PASSWORD_TOO_SHORT", "Use at least " + MIN_LENGTH + " characters.");
        }
        if (password.length() > MAX_LENGTH) {
            throw new BadRequestException("PASSWORD_TOO_LONG", "Use at most " + MAX_LENGTH + " characters.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (DENY.contains(lower) || lower.chars().distinct().count() < 4) {
            throw new BadRequestException("PASSWORD_TOO_WEAK", "Choose a less predictable password.");
        }
        if (email != null && lower.equals(email.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("PASSWORD_TOO_WEAK", "Password must differ from your email.");
        }
    }
}
