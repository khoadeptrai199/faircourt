package com.faircourt.auth.passwordreset;

import com.faircourt.common.web.ApiException;
import com.faircourt.common.web.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Rules for a new password (Password reset design D-4; OWASP Authentication Cheat Sheet): 12 to 72 bytes (BCrypt reads
 * at most 72, so a longer one is refused, never cut), not the account's email, not a very common password. The same
 * rules are checked in the form. The list is short and written here (no extra dependency); it is not a full
 * breached-password check.
 */
public final class PasswordRules {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    private static final Set<String> COMMON = Set.of(
            "password1234",
            "password12345",
            "password123456",
            "passwordpassword",
            "p@ssw0rd1234",
            "p@ssword1234",
            "1234567890123",
            "12345678901234",
            "123456789012",
            "123456789123",
            "1234512345123",
            "qwertyuiop12",
            "qwertyuiopas",
            "qwertyuiopasdf",
            "qwertyuiop123",
            "qwerty123456",
            "qwertyqwerty",
            "1q2w3e4r5t6y",
            "1q2w3e4r5t6y7u",
            "q1w2e3r4t5y6",
            "zaq12wsxcde3",
            "asdfghjkl123",
            "asdfghjklqwe",
            "zxcvbnm12345",
            "abcdefghijkl",
            "abcdefghijklm",
            "abcabcabcabc",
            "iloveyou1234",
            "iloveyou12345",
            "letmein12345",
            "letmein123456",
            "welcome12345",
            "welcome123456",
            "administrator",
            "administrator1",
            "changeme1234",
            "changemechangeme",
            "trustno1trustno1",
            "football12345",
            "baseball12345",
            "basketball123",
            "badminton1234",
            "badminton12345",
            "shuttlecock1",
            "faircourt1234",
            "faircourt12345",
            "faircourt2026",
            "faircourtdemo",
            "faircourtdemo!1",
            "monkey123456",
            "dragon123456",
            "sunshine1234",
            "princess1234",
            "superman1234",
            "starwars1234",
            "master123456",
            "login1234567",
            "passw0rd1234",
            "passw0rdpassw0rd",
            "matkhau123456",
            "matkhau1234567",
            "vietnam123456",
            "saigon123456",
            "hochiminh123");

    private PasswordRules() {}

    /** Throws {@code VALIDATION_FAILED} when the password breaks a rule. Nothing about the account is revealed. */
    public static void validate(String email, String password) {
        if (!isAcceptable(email, password)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
    }

    static boolean isAcceptable(String email, String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return false;
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return false;
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (email != null && lower.equals(email.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        return !COMMON.contains(lower) && !oneCharacterRepeated(lower);
    }

    private static boolean oneCharacterRepeated(String s) {
        return s.chars().distinct().count() == 1;
    }
}
