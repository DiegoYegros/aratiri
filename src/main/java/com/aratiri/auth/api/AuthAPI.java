package com.aratiri.auth.api;

import com.aratiri.auth.application.port.in.AuthUseCase;
import com.aratiri.auth.domain.AuthTokens;
import com.aratiri.dto.ErrorResponse;
import com.aratiri.dto.auth.AuthRequestDTO;
import com.aratiri.dto.auth.AuthResponseDTO;
import com.aratiri.dto.auth.LogoutRequestDTO;
import com.aratiri.dto.auth.PasswordResetDTOs;
import com.aratiri.dto.auth.RefreshTokenRequestDTO;
import com.aratiri.dto.auth.RegistrationRequestDTO;
import com.aratiri.dto.auth.VerificationRequestDTO;
import com.aratiri.entity.RefreshTokenEntity;
import com.aratiri.exception.AratiriException;
import com.aratiri.service.GoogleSsoService;
import com.aratiri.service.PasswordResetService;
import com.aratiri.service.RefreshTokenService;
import com.aratiri.service.RegistrationService;
import com.aratiri.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Authentication", description = "User authentication endpoints for accessing the Aratiri Bitcoin Lightning middleware platform")
public class AuthAPI {

    private final GoogleSsoService googleSsoService;
    private final AuthUseCase authUseCase;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtil jwtUtil;
    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;

    public AuthAPI(
            GoogleSsoService googleSsoService,
            AuthUseCase authUseCase,
            RefreshTokenService refreshTokenService,
            JwtUtil jwtUtil,
            RegistrationService registrationService,
            PasswordResetService passwordResetService) {
        this.googleSsoService = googleSsoService;
        this.authUseCase = authUseCase;
        this.refreshTokenService = refreshTokenService;
        this.jwtUtil = jwtUtil;
        this.registrationService = registrationService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "User login",
            description = "Authenticates a user with username and password credentials and returns a JWT access token. " +
                    "The returned token must be included in the Authorization header as 'Bearer {token}' for all " +
                    "subsequent API calls to protected endpoints."
    )
    public ResponseEntity<AuthResponseDTO> login(@RequestBody AuthRequestDTO request) {
        AuthTokens tokens = authUseCase.login(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(new AuthResponseDTO(tokens.accessToken(), tokens.refreshToken()));
    }

    @PostMapping("/register")
    @Operation(summary = "Initiate user registration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Registration initiated successfully."),
            @ApiResponse(responseCode = "400", description = "Bad Request - Email or alias already in use.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> register(@Valid @RequestBody RegistrationRequestDTO request) {
        registrationService.initiateRegistration(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify user registration")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registration completed successfully.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid verification request, code has expired, or invalid code.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
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
        authUseCase.logout(request.getRefreshToken());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Initiate password reset")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset initiated successfully."),
            @ApiResponse(responseCode = "404", description = "Not Found - User with this email not found.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody PasswordResetDTOs.ForgotPasswordRequestDTO request) {
        passwordResetService.initiatePasswordReset(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Complete password reset")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset completed successfully."),
            @ApiResponse(responseCode = "400", description = "Bad Request - Invalid password reset request, code has expired, or invalid code.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetDTOs.ResetPasswordRequestDTO request) {
        passwordResetService.completePasswordReset(request);
        return ResponseEntity.ok().build();
    }
}
