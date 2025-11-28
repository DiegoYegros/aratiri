package com.aratiri.accounts.application.port.out;

import com.aratiri.accounts.domain.AccountUser;

import java.util.Optional;

public interface LoadUserPort {

    Optional<AccountUser> findById(String id);
}
