package com.aratiri.auth.application;

import com.aratiri.auth.application.port.in.PasswordResetCompletionCommand;
import com.aratiri.auth.application.port.in.PasswordResetPort;
import com.aratiri.auth.application.port.in.PasswordResetRequestCommand;
import com.aratiri.auth.application.port.out.*;
import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.PasswordResetToken;
import com.aratiri.shared.exception.AratiriException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Random;

@Service
public class PasswordResetAdapter implements PasswordResetPort {

    private final LoadUserPort loadUserPort;
    private final PasswordResetTokenPort passwordResetTokenPort;
    private final EmailNotificationPort emailNotificationPort;
    private final UserCommandPort userCommandPort;
    private final PasswordEncoderPort passwordEncoderPort;
    private final Clock clock;
    private final Random random = new Random();

    public PasswordResetAdapter(
            LoadUserPort loadUserPort,
            PasswordResetTokenPort passwordResetTokenPort,
            EmailNotificationPort emailNotificationPort,
            UserCommandPort userCommandPort,
            PasswordEncoderPort passwordEncoderPort,
            Clock clock
    ) {
        this.loadUserPort = loadUserPort;
        this.passwordResetTokenPort = passwordResetTokenPort;
        this.emailNotificationPort = emailNotificationPort;
        this.userCommandPort = userCommandPort;
        this.passwordEncoderPort = passwordEncoderPort;
        this.clock = clock;
    }

    @Override
    public void initiatePasswordReset(PasswordResetRequestCommand command) {
        AuthUser user = loadUserPort.findByEmail(command.email())
                .orElseThrow(() -> new AratiriException("User with this email not found.", HttpStatus.NOT_FOUND.value()));
        if (user.provider() != AuthProvider.LOCAL) {
            throw new AratiriException("This account is federated. Please log in using your identity provider.", HttpStatus.BAD_REQUEST.value());
        }
        String code = generateResetCode();
        PasswordResetToken token = new PasswordResetToken(
                user.id(),
                code,
                LocalDateTime.now(clock).plusMinutes(15)
        );
        passwordResetTokenPort.save(token);
        emailNotificationPort.sendPasswordResetEmail(user.email(), code);
    }

    @Override
    public void completePasswordReset(PasswordResetCompletionCommand command) {
        AuthUser user = loadUserPort.findByEmail(command.email())
                .orElseThrow(() -> new AratiriException("User not found.", HttpStatus.NOT_FOUND.value()));
        if (user.provider() != AuthProvider.LOCAL) {
            throw new AratiriException("This account is federated. Please log in using your identity provider.", HttpStatus.BAD_REQUEST.value());
        }
        PasswordResetToken token = passwordResetTokenPort.findByUserId(user.id())
                .orElseThrow(() -> new AratiriException("Invalid password reset request.", HttpStatus.BAD_REQUEST.value()));
        if (token.isExpired(clock)) {
            passwordResetTokenPort.deleteByUserId(user.id());
            throw new AratiriException("Password reset code has expired.", HttpStatus.BAD_REQUEST.value());
        }
        if (!token.code().equals(command.code())) {
            throw new AratiriException("Invalid password reset code", HttpStatus.BAD_REQUEST.value());
        }
        userCommandPort.updatePassword(user.id(), passwordEncoderPort.encode(command.newPassword()));
        passwordResetTokenPort.deleteByUserId(user.id());
    }

    private String generateResetCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
