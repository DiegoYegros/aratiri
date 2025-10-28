package com.aratiri.auth.infrastructure.persistence;

import com.aratiri.auth.application.port.out.LoadUserPort;
import com.aratiri.auth.application.port.out.UserCommandPort;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.infrastructure.persistence.jpa.entity.UserEntity;
import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.Role;
import com.aratiri.infrastructure.persistence.jpa.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("authUserRepositoryAdapter")
public class UserRepositoryAdapter implements LoadUserPort, UserCommandPort {

    private final UserRepository userRepository;

    public UserRepositoryAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<AuthUser> findByEmail(String email) {
        return userRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public Optional<AuthUser> findById(String id) {
        return userRepository.findById(id).map(this::toDomain);
    }

    @Override
    public AuthUser registerLocalUser(String name, String email, String encodedPassword) {
        UserEntity entity = new UserEntity();
        entity.setName(name);
        entity.setEmail(email);
        entity.setPassword(encodedPassword);
        entity.setAuthProvider(AuthProvider.LOCAL);
        entity.setRole(Role.USER);
        return toDomain(userRepository.save(entity));
    }

    @Override
    public AuthUser registerSocialUser(String name, String email, AuthProvider provider, Role role) {
        UserEntity entity = new UserEntity();
        entity.setName(name);
        entity.setEmail(email);
        entity.setPassword(null);
        entity.setAuthProvider(provider);
        entity.setRole(role == null ? Role.USER : role);
        return toDomain(userRepository.save(entity));
    }

    @Override
    public void updatePassword(String userId, String encodedPassword) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setPassword(encodedPassword);
            user.setAuthProvider(AuthProvider.LOCAL);
            userRepository.save(user);
        });
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
