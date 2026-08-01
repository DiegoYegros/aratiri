package com.aratiri.requests;

import com.aratiri.auth.application.dto.UserDTO;
import com.aratiri.auth.domain.Role;
import com.aratiri.errors.ApplicationException;
import com.aratiri.infrastructure.web.GlobalExceptionHandler;
import com.aratiri.infrastructure.web.context.AratiriContext;
import com.aratiri.infrastructure.web.context.AratiriCtx;
import com.aratiri.requests.application.dto.CreatePaymentRequestDTO;
import com.aratiri.requests.application.port.in.PaymentRequestsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentRequestsAPITest {

    @Mock
    private PaymentRequestsPort paymentRequestsPort;

    private MockMvc mockMvc;
    private PaymentRequestsAPI api;
    private AratiriContext ctx;

    @BeforeEach
    void setUp() {
        UserDTO user = new UserDTO("user-1", "Test", "test@test.com", Role.USER);
        ctx = new AratiriContext(user);
        api = new PaymentRequestsAPI(paymentRequestsPort);
        mockMvc = MockMvcBuilders.standaloneSetup(api)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new StubAratiriCtxResolver(ctx))
                .build();
    }

    @Test
    void create_rejectsBlankIdempotencyKey() {
        CreatePaymentRequestDTO request = new CreatePaymentRequestDTO();
        ApplicationException ex = assertThrows(
                ApplicationException.class,
                () -> api.create("   ", request, ctx)
        );
        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getStatus());
        verify(paymentRequestsPort, never()).create(anyString(), anyString(), any());
    }

    @Test
    void create_rejectsIdempotencyKeyWithSpaces() throws Exception {
        mockMvc.perform(post("/v1/payment-requests")
                        .header("Idempotency-Key", "bad key with spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount_sats\":1000,\"expires_in_seconds\":600}"))
                .andExpect(status().isBadRequest());
        verify(paymentRequestsPort, never()).create(anyString(), anyString(), any());
    }

    @Test
    void create_rejectsIdempotencyKeyLongerThan255() throws Exception {
        mockMvc.perform(post("/v1/payment-requests")
                        .header("Idempotency-Key", "a".repeat(256))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount_sats\":1000,\"expires_in_seconds\":600}"))
                .andExpect(status().isBadRequest());
        verify(paymentRequestsPort, never()).create(anyString(), anyString(), any());
    }

    private static final class StubAratiriCtxResolver implements HandlerMethodArgumentResolver {
        private final AratiriContext ctx;

        private StubAratiriCtxResolver(AratiriContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AratiriCtx.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory
        ) {
            return ctx;
        }
    }
}
