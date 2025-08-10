package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.dto.auth.PasswordResetDTOs;
import com.aratiri.aratiri.entity.PasswordResetData;
import com.aratiri.aratiri.entity.UserEntity;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.PasswordResetDataRepository;
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
import java.util.Random;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetDataRepository passwordResetDataRepository;
    private final EmailService emailService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void initiatePasswordReset(PasswordResetDTOs.ForgotPasswordRequestDTO request) {
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AratiriException("User with this email not found.", HttpStatus.NOT_FOUND));
        String code = generateResetCode();
        PasswordResetData resetData = new PasswordResetData();
        resetData.setCode(code);
        resetData.setUser(user);
        resetData.setExpiryDate(LocalDateTime.now().plusMinutes(15));
        passwordResetDataRepository.save(resetData);
        emailService.sendPasswordResetEmail(user.getEmail(), code);
    }

    @Override
    @Transactional
    public void completePasswordReset(PasswordResetDTOs.ResetPasswordRequestDTO request) {
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AratiriException("User not found.", HttpStatus.NOT_FOUND));
        PasswordResetData resetData = passwordResetDataRepository.findByUser(user)
                .orElseThrow(() -> new AratiriException("Invalid password reset request.", HttpStatus.BAD_REQUEST));
        if (resetData.getExpiryDate().isBefore(LocalDateTime.now())) {
            passwordResetDataRepository.delete(resetData);
            throw new AratiriException("Password reset code has expired.", HttpStatus.BAD_REQUEST);
        }
        if (!resetData.getCode().equals(request.getCode())) {
            throw new AratiriException("Invalid password reset code", HttpStatus.BAD_REQUEST);
        }
        userService.updatePassword(user, passwordEncoder.encode(request.getNewPassword()));
        passwordResetDataRepository.delete(resetData);
    }

    private String generateResetCode() {
        return String.format("%06d", new Random().nextInt(999999));
    }
}