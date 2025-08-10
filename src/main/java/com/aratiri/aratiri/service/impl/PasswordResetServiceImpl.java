package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.dto.auth.PasswordResetDTOs;
import com.aratiri.aratiri.entity.PasswordResetToken;
import com.aratiri.aratiri.entity.UserEntity;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.PasswordResetTokenRepository;
import com.aratiri.aratiri.repository.UserRepository;
import com.aratiri.aratiri.service.EmailService;
import com.aratiri.aratiri.service.PasswordResetService;
import com.aratiri.aratiri.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void initiatePasswordReset(PasswordResetDTOs.ForgotPasswordRequestDTO request) {
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AratiriException("User with this email not found.", HttpStatus.NOT_FOUND));
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setExpiryDate(LocalDateTime.now().plusHours(1));
        tokenRepository.save(resetToken);
        emailService.sendPasswordResetEmail(user.getEmail(), token);
    }

    @Override
    @Transactional
    public void completePasswordReset(PasswordResetDTOs.ResetPasswordRequestDTO request) {
        PasswordResetToken resetToken = tokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new AratiriException("Invalid password reset token.", HttpStatus.BAD_REQUEST));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            tokenRepository.delete(resetToken);
            throw new AratiriException("Password reset token has expired.", HttpStatus.BAD_REQUEST);
        }
        UserEntity user = resetToken.getUser();
        userService.updatePassword(user, passwordEncoder.encode(request.getNewPassword()));
        tokenRepository.delete(resetToken);
    }
}