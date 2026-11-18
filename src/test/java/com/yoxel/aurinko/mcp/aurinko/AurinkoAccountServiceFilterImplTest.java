package com.yoxel.aurinko.mcp.aurinko;

import com.yoxel.aurinko.mcp.support.ListOnlyAccountService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class AurinkoAccountServiceFilterImplTest {

    @Test
    void onlyAllowedTypesReturnedIgnoringCaseAndNullTypes() {
        var allowed = new AurinkoAccount(1, "icloud", "user@example.com", false);
        var service = new AurinkoAccountServiceFilterImpl((ListOnlyAccountService) userId -> {
            assertThat(userId).isEqualTo("user");
            return List.of(allowed, new AurinkoAccount(2, "Salesforce", null, true),
                    new AurinkoAccount(3, null, null, true));
        }, List.of("iCloud"));

        assertThat(service.listAccounts("user")).containsExactly(allowed);
    }

    @Test
    void deletionDelegatedWithoutFiltering() {
        var deleted = new AtomicReference<String>();
        var delegate = new AurinkoAccountService() {
            @Override
            public List<AurinkoAccount> listAccounts(String userId) {
                throw new AssertionError("Deletion must not load accounts");
            }

            @Override
            public void deleteAccount(String userId, long accountId) {
                deleted.set(userId + ":" + accountId);
            }
        };
        new AurinkoAccountServiceFilterImpl(delegate, List.of("IMAP")).deleteAccount("user", 42);
        assertThat(deleted).hasValue("user:42");
    }
}
