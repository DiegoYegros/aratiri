package com.aratiri.accounts.domain;

public record Account(
        String id,
        AccountUser user,
        long balance,
        String bitcoinAddress,
        String alias
) {

    public Account withBalance(long newBalance) {
        return new Account(id, user, newBalance, bitcoinAddress, alias);
    }

    public Account withId(String newId) {
        return new Account(newId, user, balance, bitcoinAddress, alias);
    }
}
