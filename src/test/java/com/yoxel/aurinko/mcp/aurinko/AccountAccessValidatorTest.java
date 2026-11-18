package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.mcp.support.ListOnlyAccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AccountAccessValidatorTest {

    @Test
    void unsupportedAccountTypeRejectedForDataAndAccountActions() {
        var validator = new AccountAccessValidator(new AurinkoAccountServiceFilterImpl((ListOnlyAccountService) userId ->
                List.of(new AurinkoAccount(42, "Salesforce", null, true)), List.of("IMAP", "CalDav")));
        assertThatThrownBy(() -> validator.validate("user", 42)).isInstanceOf(AccountAccessDeniedException.class);
        assertThatThrownBy(() -> validator.validateOwnership("user", 42)).isInstanceOf(AccountAccessDeniedException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ownershipAcceptsActiveAndInactiveAccounts(boolean active) {
        var validator = new AccountAccessValidator((ListOnlyAccountService) userId -> {
            assertThat(userId).isEqualTo("user-1");
            return List.of(new AurinkoAccount(42, "IMAP", "user@example.com", active));
        });

        assertThatCode(() -> validator.validateOwnership("user-1", 42)).doesNotThrowAnyException();
    }

    @Test
    void ownershipRejectsForeignAccount() {
        var validator = new AccountAccessValidator((ListOnlyAccountService) userId -> List.of(
                new AurinkoAccount(43, "IMAP", "user@example.com", true)));

        assertThatThrownBy(() -> validator.validateOwnership("user-1", 42))
                .isInstanceOf(AccountAccessDeniedException.class);
    }

    @Test
    void ownedActiveAccountAccepted() {
        var validator = new AccountAccessValidator((ListOnlyAccountService) userId -> {
            assertThat(userId).isEqualTo("user-1");
            return List.of(new AurinkoAccount(42, "IMAP", "user@example.com", true));
        });

        assertThatCode(() -> validator.validate("user-1", 42)).doesNotThrowAnyException();
    }

    @Test
    void foreignAccountRejected() {
        var validator = new AccountAccessValidator((ListOnlyAccountService) userId -> List.of(
                new AurinkoAccount(43, "IMAP", "user@example.com", true)));

        assertThatThrownBy(() -> validator.validate("user-1", 42)).isInstanceOf(AccountAccessDeniedException.class);
    }

    @Test
    void inactiveAccountRejected() {
        var validator = new AccountAccessValidator((ListOnlyAccountService) userId -> List.of(
                new AurinkoAccount(42, "IMAP", "user@example.com", false)));

        assertThatThrownBy(() -> validator.validate("user-1", 42)).isInstanceOf(AccountAccessDeniedException.class);
    }

    @Test
    void emptyAccountListRejected() {
        var validator = new AccountAccessValidator((ListOnlyAccountService) userId -> List.of());

        assertThatThrownBy(() -> validator.validate("user-1", 42)).isInstanceOf(AccountAccessDeniedException.class);
    }
}
