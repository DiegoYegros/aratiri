package com.aratiri.auth.api;

import com.aratiri.auth.application.port.in.AuthPort;
import com.aratiri.auth.application.port.in.GoogleAuthPort;
import com.aratiri.auth.application.port.in.PasswordResetCompletionCommand;
import com.aratiri.auth.application.port.in.PasswordResetPort;
import com.aratiri.auth.application.port.in.PasswordResetRequestCommand;
import com.aratiri.auth.application.port.in.RegistrationCommand;
import com.aratiri.auth.application.port.in.RegistrationPort;
import com.aratiri.auth.application.port.in.TokenRefreshPort;
import com.aratiri.auth.application.port.in.VerificationCommand;
import com.aratiri.auth.domain.AuthTokens;
import com.aratiri.shared.exception.ErrorResponse;
import com.aratiri.auth.api.dto.AuthRequestDTO;
import com.aratiri.auth.api.dto.AuthResponseDTO;
import com.aratiri.auth.api.dto.LogoutRequestDTO;
import com.aratiri.auth.api.dto.PasswordResetDTOs;
import com.aratiri.auth.api.dto.RefreshTokenRequestDTO;
import com.aratiri.auth.api.dto.RegistrationRequestDTO;
import com.aratiri.auth.api.dto.VerificationRequestDTO;
import com.aratiri.dto.users.UserDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Authentication", description = "User authentication endpoints for accessing the Aratiri Bitcoin Lightning middleware platform")
public class AuthAPI {

    private final AuthPort authPort;
    private final GoogleAuthPort googleAuthPort;
    private final TokenRefreshPort tokenRefreshPort;
    private final RegistrationPort registrationPort;
    private final PasswordResetPort passwordResetPort;

    public AuthAPI(
            AuthPort authPort,
            GoogleAuthPort googleAuthPort,
            TokenRefreshPort tokenRefreshPort,
            RegistrationPort registrationPort,
            PasswordResetPort passwordResetPort) {
        this.authPort = authPort;
        this.googleAuthPort = googleAuthPort;
        this.tokenRefreshPort = tokenRefreshPort;
        this.registrationPort = registrationPort;
        this.passwordResetPort = passwordResetPort;
    }

    @PostMapping("/login")
    @Operation(
            summary = "User login",
            description = "Authenticates a user with username and password credentials and returns a JWT access token. " +
                    "The returned token must be included in the Authorization header as 'Bearer {token}' for all " +
                    "subsequent API calls to protected endpoints."
    )
    public ResponseEntity<AuthResponseDTO> login(@RequestBody AuthRequestDTO request) {
        AuthTokens tokens = authPort.login(request.getUsername(), request.getPassword());
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
        RegistrationCommand command = new RegistrationCommand(
                request.getName(),
                request.getEmail(),
                request.getPassword(),
                request.getAlias()
        );
        registrationPort.initiateRegistration(command);
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
        VerificationCommand command = new VerificationCommand(request.getEmail(), request.getCode());
        AuthTokens tokens = registrationPort.completeRegistration(command);
        return ResponseEntity.ok(new AuthResponseDTO(tokens.accessToken(), tokens.refreshToken()));
    }

    @PostMapping("/sso/google")
    @Operation(
            summary = "Google SSO login",
            description = "Authenticates an user using a Google Client ID and returns an Aratiri JWT."
    )
    public ResponseEntity<AuthResponseDTO> googleLogin(@RequestBody String googleToken) {
        AuthTokens tokens = googleAuthPort.loginWithGoogle(googleToken);
        return ResponseEntity.ok(new AuthResponseDTO(tokens.accessToken(), tokens.refreshToken()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<AuthResponseDTO> refreshToken(@RequestBody RefreshTokenRequestDTO request) {
        AuthTokens tokens = tokenRefreshPort.refreshAccessToken(request.getRefreshToken());
        return ResponseEntity.ok(new AuthResponseDTO(tokens.accessToken(), tokens.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout")
    public ResponseEntity<Void> logout(@RequestBody LogoutRequestDTO request) {
        authPort.logout(request.getRefreshToken());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user")
    public ResponseEntity<UserDTO> me() {
        var user = authPort.getCurrentUser();
        UserDTO response = new UserDTO(user.id(), user.name(), user.email(), user.role());
        return ResponseEntity.ok(response);
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
        passwordResetPort.initiatePasswordReset(new PasswordResetRequestCommand(request.getEmail()));
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
        passwordResetPort.completePasswordReset(new PasswordResetCompletionCommand(
                request.getEmail(),
                request.getCode(),
                request.getNewPassword()
        ));
        return ResponseEntity.ok().build();
    }
}
