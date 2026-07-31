package com.aratiri.errors;

import com.aratiri.infrastructure.web.ErrorResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationExceptionTest {

  @Test
  void constructor_withMessageOnly() {
    ApplicationException ex = new ApplicationException("test message");
    assertEquals("test message", ex.getMessage());
    assertNull(ex.getStatus());
  }

  @Test
  void constructor_withMessageAndStatus() {
    ApplicationException ex = new ApplicationException("not found", 404);
    assertEquals("not found", ex.getMessage());
    assertEquals(404, ex.getStatus());
  }

  @Test
  void constructor_withMessageStatusAndCause() {
    Throwable cause = new RuntimeException("root cause");
    ApplicationException ex = new ApplicationException("wrapped", 500, cause);
    assertEquals("wrapped", ex.getMessage());
    assertEquals(500, ex.getStatus());
    assertSame(cause, ex.getCause());
  }

  @Test
  void errorResponse_constructor() {
    ErrorResponse response = new ErrorResponse("error occurred", 400);
    assertEquals("error occurred", response.getMessage());
    assertEquals(400, response.getStatus());
    assertNotNull(response.getTimestamp());
  }

  @Test
  void errorResponse_fullConstructor() {
    ErrorResponse response = new ErrorResponse("error", 500, "2024-01-01T00:00:00Z");
    assertEquals("error", response.getMessage());
    assertEquals(500, response.getStatus());
    assertEquals("2024-01-01T00:00:00Z", response.getTimestamp());
  }

  @Test
  void errorResponse_setters() {
    ErrorResponse response = new ErrorResponse("msg", 200);
    response.setMessage("new msg");
    response.setStatus(404);
    response.setTimestamp("now");
    assertEquals("new msg", response.getMessage());
    assertEquals(404, response.getStatus());
    assertEquals("now", response.getTimestamp());
  }
}
