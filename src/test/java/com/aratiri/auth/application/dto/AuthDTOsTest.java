package com.aratiri.auth.application.dto;

import com.aratiri.auth.domain.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthDTOsTest {

    @Test
    void authRequestDTO() {
        AuthRequestDTO dto = new AuthRequestDTO();
        dto.setUsername("test@example.com");
        dto.setPassword("password123");
        assertEquals("test@example.com", dto.getUsername());
        assertEquals("password123", dto.getPassword());
    }

    @Test
    void authResponseDTO() {
        AuthResponseDTO dto = AuthResponseDTO.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .build();

        assertEquals("access-token", dto.getAccessToken());
        assertEquals("refresh-token", dto.getRefreshToken());
    }

    @Test
    void registrationRequestDTO() {
        RegistrationRequestDTO dto = new RegistrationRequestDTO();
        dto.setName("Test User");
        dto.setEmail("test@example.com");
        dto.setPassword("password123");
        dto.setAlias("testalias");

        assertEquals("Test User", dto.getName());
        assertEquals("test@example.com", dto.getEmail());
        assertEquals("password123", dto.getPassword());
        assertEquals("testalias", dto.getAlias());
    }

    @Test
    void verificationRequestDTO() {
        VerificationRequestDTO dto = new VerificationRequestDTO();
        dto.setEmail("test@example.com");
        dto.setCode("123456");

        assertEquals("test@example.com", dto.getEmail());
        assertEquals("123456", dto.getCode());
    }

    @Test
    void userDTO() {
        UserDTO dto = new UserDTO("user-1", "Test User", "test@example.com", Role.USER);
        assertEquals("user-1", dto.getId());
        assertEquals("Test User", dto.getName());
        assertEquals("test@example.com", dto.getEmail());
        assertEquals(Role.USER, dto.getRole());
    }

    @Test
    void logoutRequestDTO() {
        LogoutRequestDTO dto = new LogoutRequestDTO();
        dto.setRefreshToken("refresh-token");
        assertEquals("refresh-token", dto.getRefreshToken());
    }

    @Test
    void refreshTokenRequestDTO() {
        RefreshTokenRequestDTO dto = new RefreshTokenRequestDTO();
        dto.setRefreshToken("refresh-token");
        assertEquals("refresh-token", dto.getRefreshToken());
    }

    @Test
    void tokenExchangeRequestDTO() {
        TokenExchangeRequestDTO dto = new TokenExchangeRequestDTO();
        dto.setExternalToken("external-jwt-token");
        assertEquals("external-jwt-token", dto.getExternalToken());
    }
}
