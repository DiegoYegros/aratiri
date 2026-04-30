package com.aratiri.auth.application;

import com.aratiri.auth.application.port.in.PasswordResetCompletionCommand;
import com.aratiri.auth.application.port.in.PasswordResetRequestCommand;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.auth.application.port.out.LoadUserPort;
import com.aratiri.auth.application.port.out.PasswordEncoderPort;
import com.aratiri.auth.application.port.out.PasswordResetTokenPort;
import com.aratiri.auth.application.port.out.UserCommandPort;
import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.PasswordResetToken;
import com.aratiri.auth.domain.Role;
import com.aratiri.shared.exception.AratiriException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetAdapterTest {

    @Mock
    private LoadUserPort loadUserPort;

    @Mock
    private PasswordResetTokenPort passwordResetTokenPort;

    @Mock
    private EmailNotificationPort emailNotificationPort;

    @Mock
    private UserCommandPort userCommandPort;

    @Mock
    private PasswordEncoderPort passwordEncoderPort;

    private Clock clock = Clock.fixed(Instant.parse("2025-01-01T12:00:00Z"), ZoneId.of("UTC"));

    private PasswordResetAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PasswordResetAdapter(
                loadUserPort, passwordResetTokenPort, emailNotificationPort,
                userCommandPort, passwordEncoderPort, clock);
    }

    @Test
    void initiatePasswordReset_sendsEmailForLocalUser() {
        AuthUser user = new AuthUser("user-1", "Test", "test@test.com", AuthProvider.LOCAL, Role.USER);
        when(loadUserPort.findByEmail("test@test.com")).thenReturn(Optional.of(user));

        adapter.initiatePasswordReset(new PasswordResetRequestCommand("test@test.com"));

        verify(passwordResetTokenPort).save(any(PasswordResetToken.class));
        verify(emailNotificationPort).sendPasswordResetEmail(eq("test@test.com"), anyString());
    }

    @Test
    void initiatePasswordReset_throwsForFederatedUser() {
        AuthUser user = new AuthUser("user-1", "Test", "test@test.com", AuthProvider.GOOGLE, Role.USER);
        when(loadUserPort.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        PasswordResetRequestCommand command = new PasswordResetRequestCommand("test@test.com");

        assertThrows(AratiriException.class, () -> adapter.initiatePasswordReset(command));
    }

    @Test
    void initiatePasswordReset_throwsWhenUserNotFound() {
        when(loadUserPort.findByEmail("unknown@test.com")).thenReturn(Optional.empty());
        PasswordResetRequestCommand command = new PasswordResetRequestCommand("unknown@test.com");

        assertThrows(AratiriException.class, () -> adapter.initiatePasswordReset(command));
    }

    @Test
    void completePasswordReset_updatesPassword() {
        AuthUser user = new AuthUser("user-1", "Test", "test@test.com", AuthProvider.LOCAL, Role.USER);
        PasswordResetToken token = new PasswordResetToken("user-1", "123456",
                LocalDateTime.of(2025, 1, 1, 12, 30));
        when(loadUserPort.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenPort.findByUserId("user-1")).thenReturn(Optional.of(token));
        when(passwordEncoderPort.encode("newpass")).thenReturn("encoded");

        adapter.completePasswordReset(new PasswordResetCompletionCommand("test@test.com", "123456", "newpass"));

        verify(userCommandPort).updatePassword("user-1", "encoded");
        verify(passwordResetTokenPort).deleteByUserId("user-1");
    }

    @Test
    void completePasswordReset_throwsForFederatedUser() {
        AuthUser user = new AuthUser("user-1", "Test", "test@test.com", AuthProvider.GOOGLE, Role.USER);
        when(loadUserPort.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        PasswordResetCompletionCommand command =
                new PasswordResetCompletionCommand("test@test.com", "123456", "newpass");

        assertThrows(AratiriException.class, () -> adapter.completePasswordReset(command));
    }

    @Test
    void completePasswordReset_throwsWhenTokenExpired() {
        AuthUser user = new AuthUser("user-1", "Test", "test@test.com", AuthProvider.LOCAL, Role.USER);
        PasswordResetToken token = new PasswordResetToken("user-1", "123456",
                LocalDateTime.of(2025, 1, 1, 11, 59));
        when(loadUserPort.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenPort.findByUserId("user-1")).thenReturn(Optional.of(token));
        PasswordResetCompletionCommand command =
                new PasswordResetCompletionCommand("test@test.com", "123456", "newpass");

        assertThrows(AratiriException.class, () -> adapter.completePasswordReset(command));
        verify(passwordResetTokenPort).deleteByUserId("user-1");
    }

    @Test
    void completePasswordReset_throwsWhenCodeMismatch() {
        AuthUser user = new AuthUser("user-1", "Test", "test@test.com", AuthProvider.LOCAL, Role.USER);
        PasswordResetToken token = new PasswordResetToken("user-1", "654321",
                LocalDateTime.of(2025, 1, 1, 12, 30));
        when(loadUserPort.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenPort.findByUserId("user-1")).thenReturn(Optional.of(token));
        PasswordResetCompletionCommand command =
                new PasswordResetCompletionCommand("test@test.com", "123456", "newpass");

        assertThrows(AratiriException.class, () -> adapter.completePasswordReset(command));
        verify(userCommandPort, never()).updatePassword(anyString(), anyString());
    }
}
