package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.dto.accounts.CreateAccountRequestDTO;
import com.aratiri.aratiri.dto.auth.AuthResponseDTO;
import com.aratiri.aratiri.dto.auth.RegistrationRequestDTO;
import com.aratiri.aratiri.dto.auth.VerificationRequestDTO;
import com.aratiri.aratiri.entity.UserEntity;
import com.aratiri.aratiri.entity.VerificationData;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.UserRepository;
import com.aratiri.aratiri.repository.VerificationDataRepository;
import com.aratiri.aratiri.service.*;
import com.aratiri.aratiri.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private final UserRepository userRepository;
    private final VerificationDataRepository verificationDataRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final AccountsService accountsService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional
    public void initiateRegistration(RegistrationRequestDTO request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AratiriException("Email is already in use", HttpStatus.BAD_REQUEST);
        }

        if (accountsService.existsByAlias(request.getAlias())) {
            throw new AratiriException("Alias is already in use", HttpStatus.BAD_REQUEST);
        }

        String code = generateVerificationCode();
        VerificationData verificationData = VerificationData.builder()
                .email(request.getEmail())
                .name(request.getName())
                .password(passwordEncoder.encode(request.getPassword()))
                .alias(request.getAlias())
                .code(code)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        verificationDataRepository.save(verificationData);
        emailService.sendVerificationEmail(request.getEmail(), code);
    }

    @Override
    @Transactional
    public AuthResponseDTO completeRegistration(VerificationRequestDTO request) {
        VerificationData verificationData = verificationDataRepository.findById(request.getEmail())
                .orElseThrow(() -> new AratiriException("Invalid verification request", HttpStatus.BAD_REQUEST));
        if (verificationData.getExpiresAt().isBefore(LocalDateTime.now())) {
            verificationDataRepository.delete(verificationData);
            throw new AratiriException("Verification code has expired", HttpStatus.BAD_REQUEST);
        }
        if (!verificationData.getCode().equals(request.getCode())) {
            throw new AratiriException("Invalid verification code", HttpStatus.BAD_REQUEST);
        }
        userService.register(verificationData.getName(), verificationData.getEmail(), verificationData.getPassword());
        UserEntity user = userRepository.findByEmail(verificationData.getEmail()).get();
        CreateAccountRequestDTO createAccountRequest = new CreateAccountRequestDTO();
        createAccountRequest.setUserId(user.getId());
        createAccountRequest.setAlias(verificationData.getAlias());
        accountsService.createAccount(createAccountRequest, user.getId());
        verificationDataRepository.delete(verificationData);
        String accessToken = jwtUtil.generateToken(user.getEmail());
        String refreshToken = refreshTokenService.createRefreshToken(user.getId()).getToken();
        return new AuthResponseDTO(accessToken, refreshToken);
    }

    private String generateVerificationCode() {
        return String.format("%06d", new Random().nextInt(999999));
    }
}
