package com.aratiri.aratiri.controller;

import com.aratiri.aratiri.dto.auth.AuthRequestDTO;
import com.aratiri.aratiri.dto.auth.AuthResponseDTO;
import com.aratiri.aratiri.service.AuthService;
import com.aratiri.aratiri.service.GoogleSsoService;
import com.aratiri.aratiri.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

    @PostMapping("/sso/google")
    @Operation(
            summary = "Google SSO login",
            description = "Authenticates an user using a Google Client ID and returns an Aratiri JWT."
    )
    public ResponseEntity<AuthResponseDTO> googleLogin(@RequestBody String googleToken) {
        String jwt = googleSsoService.loginWithGoogle(googleToken);
        return ResponseEntity.ok(new AuthResponseDTO(jwt));
    }
}