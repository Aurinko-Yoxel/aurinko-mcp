package com.yoxel.aurinko.mcp.aurinko;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AccountAccessValidatorTest {

    @Test
    void ownedActiveAccountAccepted() {
        var validator = new AccountAccessValidator(userId -> {
            assertThat(userId).isEqualTo("user-1");
            return List.of(new AurinkoAccount(42, "IMAP", "user@example.com", true));
        });

        assertThatCode(() -> validator.validate("user-1", 42)).doesNotThrowAnyException();
    }

    @Test
    void foreignAccountRejected() {
        var validator = new AccountAccessValidator(userId -> List.of(
                new AurinkoAccount(43, "IMAP", "user@example.com", true)));

        assertThatThrownBy(() -> validator.validate("user-1", 42)).isInstanceOf(AccountAccessDeniedException.class);
    }

    @Test
    void inactiveAccountRejected() {
        var validator = new AccountAccessValidator(userId -> List.of(
                new AurinkoAccount(42, "IMAP", "user@example.com", false)));

        assertThatThrownBy(() -> validator.validate("user-1", 42)).isInstanceOf(AccountAccessDeniedException.class);
    }

    @Test
    void emptyAccountListRejected() {
        var validator = new AccountAccessValidator(userId -> List.of());

        assertThatThrownBy(() -> validator.validate("user-1", 42)).isInstanceOf(AccountAccessDeniedException.class);
    }
}
