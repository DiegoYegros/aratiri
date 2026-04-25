package com.aratiri.payments.infrastructure.json;

import com.aratiri.shared.exception.AratiriException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

public class JsonUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private JsonUtils() {
    }

    public static String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new AratiriException("Failed to map string to json: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new AratiriException("Failed to parse json: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }
}
