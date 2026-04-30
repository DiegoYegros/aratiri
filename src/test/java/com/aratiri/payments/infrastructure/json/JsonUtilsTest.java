package com.aratiri.payments.infrastructure.json;

import com.aratiri.shared.exception.AratiriException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilsTest {

    record TestPayload(String name, int value) {}

    @Test
    void toJson_serializesObject() {
        TestPayload payload = new TestPayload("test", 42);
        String json = JsonUtils.toJson(payload);

        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"test\""));
        assertTrue(json.contains("\"value\""));
        assertTrue(json.contains("42"));
    }

    @Test
    void fromJson_deserializesObject() {
        String json = "{\"name\":\"test\",\"value\":42}";
        TestPayload result = JsonUtils.fromJson(json, TestPayload.class);

        assertEquals("test", result.name());
        assertEquals(42, result.value());
    }

    @Test
    void fromJson_throwsOnInvalidJson() {
        assertThrows(AratiriException.class, () -> JsonUtils.fromJson("invalid", TestPayload.class));
    }

    @Test
    void toJson_handlesCircularReference() {
        Object self = new Object() {
            final Object self = this;
        };

        assertThrows(AratiriException.class, () -> JsonUtils.toJson(self));
    }
}
