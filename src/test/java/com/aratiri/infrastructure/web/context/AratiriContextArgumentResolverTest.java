package com.aratiri.infrastructure.web.context;

import com.aratiri.auth.application.port.in.AuthPort;
import com.aratiri.auth.domain.AuthProvider;
import com.aratiri.auth.domain.AuthUser;
import com.aratiri.auth.domain.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AratiriContextArgumentResolverTest {

    @Mock
    private AuthPort authPort;

    @InjectMocks
    private AratiriContextArgumentResolver resolver;

    @Test
    void supportsParameter_withAratiriCtxAnnotationAndAratiriContextType() throws NoSuchMethodException {
        MethodParameter param = createParam("methodWithAratiriCtx");
        assertTrue(resolver.supportsParameter(param));
    }

    @Test
    void supportsParameter_withoutAratiriCtxAnnotation() throws NoSuchMethodException {
        MethodParameter param = createParam("methodWithoutAnnotation");
        assertFalse(resolver.supportsParameter(param));
    }

    @Test
    void resolveArgument_returnsAratiriContext() throws Exception {
        AuthUser authUser = new AuthUser("user-1", "Test", "test@example.com", AuthProvider.LOCAL, Role.USER);
        when(authPort.getCurrentUser()).thenReturn(authUser);

        MethodParameter param = createParam("methodWithAratiriCtx");
        Object result = resolver.resolveArgument(param, null, null, null);

        assertNotNull(result);
        assertInstanceOf(AratiriContext.class, result);
        AratiriContext ctx = (AratiriContext) result;
        assertEquals("user-1", ctx.user().getId());
        assertEquals("Test", ctx.user().getName());
        assertEquals("test@example.com", ctx.user().getEmail());
    }

    @Test
    void resolveArgument_authPortThrows_returnsNull() throws Exception {
        when(authPort.getCurrentUser()).thenThrow(new RuntimeException("auth failed"));

        MethodParameter param = createParam("methodWithAratiriCtx");
        Object result = resolver.resolveArgument(param, null, null, null);

        assertNull(result);
    }

    private MethodParameter createParam(String methodName) throws NoSuchMethodException {
        if (methodName.equals("methodWithAratiriCtx")) {
            return MethodParameter.forExecutable(
                    TestController.class.getDeclaredMethod("methodWithAratiriCtx", AratiriContext.class), 0);
        } else {
            return MethodParameter.forExecutable(
                    TestController.class.getDeclaredMethod("methodWithoutAnnotation", String.class), 0);
        }
    }

    static class TestController {
        void methodWithAratiriCtx(@AratiriCtx AratiriContext ctx) {
            // Signature-only method used to create a MethodParameter.
        }

        void methodWithoutAnnotation(String input) {
            // Signature-only method used to create a MethodParameter.
        }
    }
}
