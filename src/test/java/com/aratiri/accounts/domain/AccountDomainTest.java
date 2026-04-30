package com.aratiri.accounts.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountDomainTest {

    @Test
    void account_record() {
        AccountUser user = new AccountUser("user-1");
        Account account = new Account("acc-1", user, 1000L, "bc1qabc", "myalias");

        assertEquals("acc-1", account.id());
        assertEquals("user-1", account.user().id());
        assertEquals(1000L, account.balance());
        assertEquals("bc1qabc", account.bitcoinAddress());
        assertEquals("myalias", account.alias());
    }

    @Test
    void account_withBalance() {
        AccountUser user = new AccountUser("user-1");
        Account account = new Account("acc-1", user, 1000L, "bc1qabc", "myalias");

        Account updated = account.withBalance(2000L);

        assertEquals(2000L, updated.balance());
        assertEquals("acc-1", updated.id());
        assertEquals("bc1qabc", updated.bitcoinAddress());
        assertEquals("myalias", updated.alias());
    }

    @Test
    void account_withId() {
        AccountUser user = new AccountUser("user-1");
        Account account = new Account("acc-1", user, 1000L, "bc1qabc", "myalias");

        Account updated = account.withId("acc-2");

        assertEquals("acc-2", updated.id());
        assertEquals(1000L, updated.balance());
    }

    @Test
    void accountUser_record() {
        AccountUser user = new AccountUser("user-1");
        assertEquals("user-1", user.id());
    }
}
