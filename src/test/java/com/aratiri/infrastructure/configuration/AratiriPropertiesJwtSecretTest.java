package com.aratiri.infrastructure.configuration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AratiriPropertiesJwtSecretTest {

    @Test
    void validateJwtSecret_rejectsNull() {
        AratiriProperties properties = new AratiriProperties();
        properties.setJwtSecret(null);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, properties::validateJwtSecret);
        assertTrue(thrown.getMessage().contains("JWT_SECRET"));
        assertTrue(thrown.getMessage().contains("non-blank"));
    }

    @Test
    void validateJwtSecret_rejectsBlank() {
        AratiriProperties properties = new AratiriProperties();
        properties.setJwtSecret("   ");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, properties::validateJwtSecret);
        assertTrue(thrown.getMessage().contains("non-blank"));
    }

    @Test
    void validateJwtSecret_rejectsEmpty() {
        AratiriProperties properties = new AratiriProperties();
        properties.setJwtSecret("");

        assertThrows(IllegalStateException.class, properties::validateJwtSecret);
    }

    @Test
    void validateJwtSecret_rejectsShorterThan32Utf8Bytes() {
        AratiriProperties properties = new AratiriProperties();
        // 31 ASCII chars = 31 UTF-8 bytes
        properties.setJwtSecret("a".repeat(AratiriProperties.MIN_JWT_SECRET_UTF8_BYTES - 1));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, properties::validateJwtSecret);
        assertTrue(thrown.getMessage().contains("at least " + AratiriProperties.MIN_JWT_SECRET_UTF8_BYTES));
        assertTrue(thrown.getMessage().contains("got " + (AratiriProperties.MIN_JWT_SECRET_UTF8_BYTES - 1)));
    }

    @Test
    void validateJwtSecret_acceptsExactly32Utf8Bytes() {
        AratiriProperties properties = new AratiriProperties();
        properties.setJwtSecret("a".repeat(AratiriProperties.MIN_JWT_SECRET_UTF8_BYTES));

        assertDoesNotThrow(properties::validateJwtSecret);
    }

    @Test
    void validateJwtSecret_acceptsLongerThan32Utf8Bytes() {
        AratiriProperties properties = new AratiriProperties();
        properties.setJwtSecret("test-integration-jwt-secret-key-must-be-at-least-256-bits-long");

        assertDoesNotThrow(properties::validateJwtSecret);
    }

    @Test
    void validateJwtSecret_countsUtf8BytesNotChars() {
        AratiriProperties properties = new AratiriProperties();
        // 16 × U+00E9 (é) = 16 chars but 32 UTF-8 bytes
        properties.setJwtSecret("é".repeat(16));

        assertDoesNotThrow(properties::validateJwtSecret);

        // 15 × é = 30 UTF-8 bytes — must fail
        properties.setJwtSecret("é".repeat(15));
        IllegalStateException thrown = assertThrows(IllegalStateException.class, properties::validateJwtSecret);
        assertTrue(thrown.getMessage().contains("got 30"));
    }
}
