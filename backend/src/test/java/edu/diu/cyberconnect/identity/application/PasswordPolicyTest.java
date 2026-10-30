package edu.diu.cyberconnect.identity.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.diu.cyberconnect.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsLongPassphrases() {
        assertThatCode(() -> PasswordPolicy.validate("correct horse battery staple", "a@b.test")).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortDenyListedAndEmailPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.validate("short", "a@b.test"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("12");
        assertThatThrownBy(() -> PasswordPolicy.validate("password1234", "a@b.test"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("aaaaaaaaaaaaaa", "a@b.test"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("someone@example.test", "someone@example.test"))
                .isInstanceOf(BadRequestException.class);
    }
}
