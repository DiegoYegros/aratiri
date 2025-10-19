package com.aratiri.accounts.infrastructure.users;

import com.aratiri.accounts.application.port.out.LoadUserPort;
import com.aratiri.accounts.domain.AccountUser;
import com.aratiri.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserRepositoryAdapter implements LoadUserPort {

    private final UserRepository userRepository;

    public UserRepositoryAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<AccountUser> findById(String id) {
        return userRepository.findById(id).map(user -> new AccountUser(user.getId()));
    }
}