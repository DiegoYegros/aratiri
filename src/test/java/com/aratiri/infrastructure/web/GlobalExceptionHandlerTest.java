package com.aratiri.infrastructure.web;

import com.aratiri.shared.exception.AratiriException;
import com.aratiri.shared.exception.ErrorResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleIllegalArgumentException() {
        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("bad input"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("bad input"));
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    void handleGeneralException() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(
                new RuntimeException("something broke"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
        assertNotNull(response.getBody().getTimestamp());
    }

    @Test
    void handleValidationException() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("request", "amount", "must be positive");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                mock(org.springframework.core.MethodParameter.class), bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("amount"));
    }

    @Test
    void handleValidationException_noFieldErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                mock(org.springframework.core.MethodParameter.class), bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid request", response.getBody().getMessage());
    }

    @Test
    void handleHttpMessageNotReadableException() {
        ResponseEntity<ErrorResponse> response = handler.handleHttpMessageNotReadableException(
                new HttpMessageNotReadableException("missing body", new MockHttpInputMessage(new byte[0])));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    void handleBadCredentialsException() {
        ResponseEntity<ErrorResponse> response = handler.handleBadCredentialsException(
                new BadCredentialsException("invalid credentials"));
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getStatus());
    }

    @Test
    void handleStatusRuntimeException() {
        StatusRuntimeException ex = new StatusRuntimeException(Status.INTERNAL);
        ResponseEntity<ErrorResponse> response = handler.handleStatusRuntimeException(ex);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
    }

    @Test
    void handleNoResourceFoundException() {
        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFoundException(
                new NoResourceFoundException(HttpMethod.GET, "/notfound", "not found"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    void handleMissingServletRequestParameterException() {
        ResponseEntity<ErrorResponse> response = handler.handleMissingServletRequestParameterException(
                new MissingServletRequestParameterException("id", "String"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    void handleMethodArgumentTypeMismatchException() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "id", null, null);
        ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentTypeMismatchException(ex);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    void handleAuthorizationDeniedException() {
        ResponseEntity<ErrorResponse> response = handler.handleAuthorizationDeniedException(
                new AuthorizationDeniedException("access denied"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    void handleAratiriException_withStatus() {
        AratiriException ex = new AratiriException("not found", HttpStatus.NOT_FOUND.value());
        ResponseEntity<ErrorResponse> response = handler.handleAratiriException(ex);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
        assertTrue(response.getBody().getMessage().contains("not found"));
    }

    @Test
    void handleAratiriException_withNullStatus() {
        AratiriException ex = new AratiriException("unknown error");
        ResponseEntity<ErrorResponse> response = handler.handleAratiriException(ex);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
    }

    @Test
    void handleAratiriException_withInvalidStatus() {
        AratiriException ex = new AratiriException("error", 9999);
        ResponseEntity<ErrorResponse> response = handler.handleAratiriException(ex);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
    }
}
