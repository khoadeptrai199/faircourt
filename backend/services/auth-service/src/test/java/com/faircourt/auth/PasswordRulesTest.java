package com.faircourt.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faircourt.auth.passwordreset.PasswordRules;
import com.faircourt.common.web.ApiException;
import org.junit.jupiter.api.Test;

class PasswordRulesTest {

    private static final String EMAIL = "host@faircourt.local";

    @Test
    void aTwelveCharacterPassphraseIsAccepted() {
        PasswordRules.validate(EMAIL, "correct horse battery");
        PasswordRules.validate(EMAIL, "abcdefghijk1");
    }

    @Test
    void shorterThanTwelveCharactersIsRefused() {
        assertThatThrownBy(() -> PasswordRules.validate(EMAIL, "elevenchars")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PasswordRules.validate(EMAIL, "")).isInstanceOf(ApiException.class);
    }

    @Test
    void moreThanSeventyTwoBytesIsRefusedNotCut() {
        PasswordRules.validate(EMAIL, "a1".repeat(36)); // exactly 72 bytes
        assertThatThrownBy(() -> PasswordRules.validate(EMAIL, "a".repeat(73))).isInstanceOf(ApiException.class);
        // 37 two-byte characters = 74 bytes although only 37 characters
        assertThatThrownBy(() -> PasswordRules.validate(EMAIL, "é".repeat(37))).isInstanceOf(ApiException.class);
    }

    @Test
    void theEmailAndCommonPasswordsAreRefusedIgnoringCase() {
        assertThatThrownBy(() -> PasswordRules.validate(EMAIL, "HOST@faircourt.LOCAL"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PasswordRules.validate(EMAIL, "PassWord1234")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> PasswordRules.validate(EMAIL, "FairCourtDemo!1"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void oneRepeatedCharacterIsRefused() {
        assertThatThrownBy(() -> PasswordRules.validate(EMAIL, "z".repeat(20))).isInstanceOf(ApiException.class);
    }

    @Test
    void theErrorSaysNothingAboutWhichRuleFailed() {
        assertThatThrownBy(() -> PasswordRules.validate(EMAIL, "short"))
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.getMessage()).isEqualTo("The request is invalid."));
    }
}
