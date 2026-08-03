package com.aratiri.spark;

import com.aratiri.auth.application.dto.UserDTO;
import com.aratiri.auth.domain.Role;
import com.aratiri.infrastructure.web.GlobalExceptionHandler;
import com.aratiri.infrastructure.web.context.AratiriContext;
import com.aratiri.infrastructure.web.context.AratiriCtx;
import com.aratiri.spark.application.dto.SparkWalletDTO;
import com.aratiri.spark.application.port.in.SparkWalletPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SparkWalletAPITest {

    private static final String IDENTITY_KEY =
            "02aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899";
    private static final String SPARK_ADDRESS = "sparkrt1qphk9clx4jh5gp0wjkwsc3h28m3y3n4m2v2a8j7v5q3z2";

    @Mock
    private SparkWalletPort sparkWalletPort;

    private MockMvc mockMvc;
    private AratiriContext ctx;

    @BeforeEach
    void setUp() {
        UserDTO user = new UserDTO("user-1", "Test", "test@test.com", Role.USER);
        ctx = new AratiriContext(user);
        SparkAPI api = new SparkAPI(sparkWalletPort);
        mockMvc = MockMvcBuilders.standaloneSetup(api)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new StubAratiriCtxResolver(ctx))
                .build();
    }

    @Test
    void getWallet_returnsOkWhenNoWallet() throws Exception {
        when(sparkWalletPort.get("user-1")).thenReturn(Optional.empty());
        mockMvc.perform(get("/v1/spark/wallet"))
                .andExpect(status().isOk());
        verify(sparkWalletPort).get("user-1");
    }

    @Test
    void getWallet_returnsDtoWhenPresent() throws Exception {
        when(sparkWalletPort.get("user-1")).thenReturn(Optional.of(dto()));
        mockMvc.perform(get("/v1/spark/wallet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spark_address").value(SPARK_ADDRESS))
                .andExpect(jsonPath("$.identity_public_key").value(IDENTITY_KEY))
                .andExpect(jsonPath("$.network").value("REGTEST"))
                .andExpect(jsonPath("$.account_index").value(0))
                .andExpect(jsonPath("$.backup_verified").value(false))
                .andExpect(jsonPath("$.privacy_enabled").value(false));
    }

    @Test
    void register_returns201WithLocationAndDto() throws Exception {
        when(sparkWalletPort.register(eq("user-1"), any())).thenReturn(dto());
        mockMvc.perform(post("/v1/spark/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.network").value("REGTEST"))
                .andExpect(jsonPath("$.account_index").value(0));
        verify(sparkWalletPort).register(eq("user-1"), any());
    }

    @Test
    void register_rejectsInvalidIdentityKey() throws Exception {
        mockMvc.perform(post("/v1/spark/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody().replace(IDENTITY_KEY, "04" + IDENTITY_KEY.substring(2))))
                .andExpect(status().isBadRequest());
        verify(sparkWalletPort, never()).register(anyString(), any());
    }

    @Test
    void register_rejectsMissingFields() throws Exception {
        mockMvc.perform(post("/v1/spark/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verify(sparkWalletPort, never()).register(anyString(), any());
    }

    @Test
    void register_rejectsNegativeAccountIndex() throws Exception {
        mockMvc.perform(post("/v1/spark/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody().replace("\"account_index\":0", "\"account_index\":-1")))
                .andExpect(status().isBadRequest());
        verify(sparkWalletPort, never()).register(anyString(), any());
    }

    @Test
    void backupVerified_updatesFlag() throws Exception {
        when(sparkWalletPort.setBackupVerified("user-1", true)).thenReturn(dto());
        mockMvc.perform(post("/v1/spark/backup-verified")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"backup_verified\":true}"))
                .andExpect(status().isOk());
        verify(sparkWalletPort).setBackupVerified("user-1", true);
    }

    @Test
    void backupVerified_rejectsMissingField() throws Exception {
        mockMvc.perform(post("/v1/spark/backup-verified")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verify(sparkWalletPort, never()).setBackupVerified(anyString(), any(boolean.class));
    }

    @Test
    void privacy_updatesFlag() throws Exception {
        when(sparkWalletPort.setPrivacyEnabled("user-1", true)).thenReturn(dto());
        mockMvc.perform(post("/v1/spark/privacy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"privacy_enabled\":true}"))
                .andExpect(status().isOk());
        verify(sparkWalletPort).setPrivacyEnabled("user-1", true);
    }

    @Test
    void privacy_rejectsMissingField() throws Exception {
        mockMvc.perform(post("/v1/spark/privacy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verify(sparkWalletPort, never()).setPrivacyEnabled(anyString(), any(boolean.class));
    }

    @Test
    void forget_returns204() throws Exception {
        mockMvc.perform(delete("/v1/spark/wallet"))
                .andExpect(status().isNoContent());
        verify(sparkWalletPort).forget("user-1");
    }

    private static String registerBody() {
        return "{\"identity_public_key\":\"" + IDENTITY_KEY
                + "\",\"spark_address\":\"" + SPARK_ADDRESS
                + "\",\"network\":\"REGTEST\",\"account_index\":0}";
    }

    private static SparkWalletDTO dto() {
        return SparkWalletDTO.builder()
                .sparkAddress(SPARK_ADDRESS)
                .identityPublicKey(IDENTITY_KEY)
                .network("REGTEST")
                .accountIndex(0)
                .backupVerified(false)
                .privacyEnabled(false)
                .build();
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
