package com.aratiri.auth.infrastructure.persistence;

import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.Role;
import com.aratiri.infrastructure.persistence.jpa.entity.UserEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRepositoryAdapterTest {

    @Mock
    private UserRepository userRepository;

    private UserRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserRepositoryAdapter(userRepository);
    }

    @Test
    void findByEmail_returnsUserWhenFound() {
        UserEntity entity = new UserEntity();
        entity.setId("user-1");
        entity.setName("Test");
        entity.setEmail("test@example.com");
        entity.setAuthProvider(AuthProvider.LOCAL);
        entity.setRole(Role.USER);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(entity));

        Optional<AuthUser> result = adapter.findByEmail("test@example.com");

        assertTrue(result.isPresent());
        assertEquals("user-1", result.get().id());
        assertEquals("Test", result.get().name());
        assertEquals("test@example.com", result.get().email());
        assertEquals(AuthProvider.LOCAL, result.get().provider());
        assertEquals(Role.USER, result.get().role());
    }

    @Test
    void findByEmail_returnsEmptyWhenNotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        Optional<AuthUser> result = adapter.findByEmail("unknown@example.com");

        assertTrue(result.isEmpty());
    }

    @Test
    void findById_returnsUserWhenFound() {
        UserEntity entity = new UserEntity();
        entity.setId("user-1");
        entity.setName("Test");
        entity.setEmail("test@example.com");
        entity.setAuthProvider(AuthProvider.GOOGLE);
        entity.setRole(Role.ADMIN);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(entity));

        Optional<AuthUser> result = adapter.findById("user-1");

        assertTrue(result.isPresent());
        assertEquals(AuthProvider.GOOGLE, result.get().provider());
        assertEquals(Role.ADMIN, result.get().role());
    }

    @Test
    void findById_returnsEmptyWhenNotFound() {
        when(userRepository.findById("unknown")).thenReturn(Optional.empty());

        Optional<AuthUser> result = adapter.findById("unknown");

        assertTrue(result.isEmpty());
    }

    @Test
    void registerLocalUser_createsAndReturnsUser() {
        UserEntity savedEntity = new UserEntity();
        savedEntity.setId("user-1");
        savedEntity.setName("Test");
        savedEntity.setEmail("test@example.com");
        savedEntity.setAuthProvider(AuthProvider.LOCAL);
        savedEntity.setRole(Role.USER);
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedEntity);

        AuthUser result = adapter.registerLocalUser("Test", "test@example.com", "encoded123");

        assertEquals("user-1", result.id());
        assertEquals("Test", result.name());
        assertEquals(AuthProvider.LOCAL, result.provider());
        assertEquals(Role.USER, result.role());

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertEquals("Test", captor.getValue().getName());
        assertEquals("test@example.com", captor.getValue().getEmail());
        assertEquals("encoded123", captor.getValue().getPassword());
    }

    @Test
    void registerSocialUser_createsWithDefaultRole() {
        UserEntity savedEntity = new UserEntity();
        savedEntity.setId("user-2");
        savedEntity.setName("Social");
        savedEntity.setEmail("social@example.com");
        savedEntity.setAuthProvider(AuthProvider.GOOGLE);
        savedEntity.setRole(Role.USER);
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedEntity);

        AuthUser result = adapter.registerSocialUser("Social", "social@example.com", AuthProvider.GOOGLE, null);

        assertEquals("user-2", result.id());
        assertEquals(Role.USER, result.role());
    }

    @Test
    void registerSocialUser_createsWithExplicitRole() {
        UserEntity savedEntity = new UserEntity();
        savedEntity.setId("user-3");
        savedEntity.setName("Admin");
        savedEntity.setEmail("admin@example.com");
        savedEntity.setAuthProvider(AuthProvider.EXTERNAL);
        savedEntity.setRole(Role.ADMIN);
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedEntity);

        AuthUser result = adapter.registerSocialUser("Admin", "admin@example.com", AuthProvider.EXTERNAL, Role.ADMIN);

        assertEquals(Role.ADMIN, result.role());
    }

    @Test
    void updatePassword_updatesWhenUserFound() {
        UserEntity entity = new UserEntity();
        entity.setId("user-1");
        entity.setEmail("test@example.com");
        entity.setAuthProvider(AuthProvider.GOOGLE);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(entity));

        adapter.updatePassword("user-1", "newEncodedPassword");

        verify(userRepository).save(entity);
        assertEquals("newEncodedPassword", entity.getPassword());
        assertEquals(AuthProvider.LOCAL, entity.getAuthProvider());
    }

    @Test
    void updatePassword_doesNothingWhenUserNotFound() {
        when(userRepository.findById("unknown")).thenReturn(Optional.empty());

        adapter.updatePassword("unknown", "newEncodedPassword");

        verify(userRepository, never()).save(any());
    }
}
