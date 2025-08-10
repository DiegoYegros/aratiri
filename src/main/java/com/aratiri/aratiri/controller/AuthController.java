package com.aratiri.aratiri.controller;

import com.aratiri.aratiri.dto.auth.*;
import com.aratiri.aratiri.entity.RefreshTokenEntity;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.service.*;
import com.aratiri.aratiri.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@AllArgsConstructor
@Tag(name = "Authentication", description = "User authentication endpoints for accessing the Aratiri Bitcoin Lightning middleware platform")
public class AuthController {

    private final GoogleSsoService googleSsoService;
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtil jwtUtil;
    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    @Operation(
            summary = "User login",
            description = "Authenticates a user with username and password credentials and returns a JWT access token. " +
                    "The returned token must be included in the Authorization header as 'Bearer {token}' for all " +
                    "subsequent API calls to protected endpoints."
    )
    public ResponseEntity<AuthResponseDTO> login(@RequestBody AuthRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    @Operation(summary = "Initiate user registration")
    public ResponseEntity<Void> register(@Valid @RequestBody RegistrationRequestDTO request) {
        registrationService.initiateRegistration(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify user registration")
    public ResponseEntity<AuthResponseDTO> verify(@Valid @RequestBody VerificationRequestDTO request) {
        return ResponseEntity.ok(registrationService.completeRegistration(request));
    }

    @PostMapping("/sso/google")
    @Operation(
            summary = "Google SSO login",
            description = "Authenticates an user using a Google Client ID and returns an Aratiri JWT."
    )
    public ResponseEntity<AuthResponseDTO> googleLogin(@RequestBody String googleToken) {
        return ResponseEntity.ok(googleSsoService.loginWithGoogle(googleToken));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<AuthResponseDTO> refreshToken(@RequestBody RefreshTokenRequestDTO request) {
        return refreshTokenService.findByToken(request.getRefreshToken())
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshTokenEntity::getUser)
                .map(user -> {
                    String accessToken = jwtUtil.generateTokenFromUsername(user.getEmail());
                    return ResponseEntity.ok(new AuthResponseDTO(accessToken, request.getRefreshToken()));
                })
                .orElseThrow(() -> new AratiriException("Refresh token is not in database!", HttpStatus.BAD_REQUEST));
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout")
    public ResponseEntity<Void> logout(@RequestBody LogoutRequestDTO request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Initiate password reset")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody PasswordResetDTOs.ForgotPasswordRequestDTO request) {
        passwordResetService.initiatePasswordReset(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Complete password reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetDTOs.ResetPasswordRequestDTO request) {
        passwordResetService.completePasswordReset(request);
        return ResponseEntity.ok().build();
    }
}