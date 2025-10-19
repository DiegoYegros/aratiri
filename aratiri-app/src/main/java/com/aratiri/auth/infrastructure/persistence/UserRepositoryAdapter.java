package com.aratiri.auth.infrastructure.persistence;

import com.aratiri.auth.application.port.out.LoadUserPort;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.entity.UserEntity;
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
    public Optional<AuthUser> findByEmail(String email) {
        return userRepository.findByEmail(email).map(this::toDomain);
    }

    private AuthUser toDomain(UserEntity entity) {
        return new AuthUser(
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                entity.getAuthProvider(),
                entity.getRole()
        );
    }
}
