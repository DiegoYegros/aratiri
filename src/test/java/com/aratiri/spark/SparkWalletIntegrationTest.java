package com.aratiri.spark;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.auth.application.dto.AuthResponseDTO;
import com.aratiri.auth.application.dto.RegistrationRequestDTO;
import com.aratiri.auth.application.dto.VerificationRequestDTO;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.infrastructure.persistence.jpa.repository.SparkWalletRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.VerificationDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class SparkWalletIntegrationTest extends AbstractIntegrationTest {

    private static final String IDENTITY_KEY =
            "02aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899";
    private static final String SPARK_ADDRESS = "sparkrt1qphk9clx4jh5gp0wjkwsc3h28m3y3n4m2v2a8j7v5q3z2";

    @MockitoBean
    private EmailNotificationPort emailNotificationPort;

    @MockitoBean
    private CurrencyConversionPort currencyConversionPort;

    @MockitoBean
    private LightningAddressPort lightningAddressPort;

    @Autowired
    private SparkWalletRepository sparkWalletRepository;

    @Autowired
    private VerificationDataRepository verificationDataRepository;

    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUpUsers() {
        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("usd", BigDecimal.valueOf(50000)));
        when(lightningAddressPort.generateTaprootAddress()).thenAnswer(invocation ->
                "bc1p_spark_it_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        doAnswer(invocation -> null).when(emailNotificationPort).sendVerificationEmail(anyString(), anyString());

        ownerToken = registerAndVerify("owner-spark@example.com", "Owner Spark", "ownerspark");
        otherToken = registerAndVerify("other-spark@example.com", "Other Spark", "otherspark");
    }

    @Test
    @DisplayName("Full lifecycle: get-null -> register -> flags -> forget -> re-register")
    void full_lifecycle() {
        // No wallet yet: GET returns 200 with an empty body, not 404.
        webTestClient().get().uri("/v1/spark/wallet")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .isEmpty();

        Map<?, ?> created = webTestClient().post().uri("/v1/spark/wallets")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody(IDENTITY_KEY, SPARK_ADDRESS, "REGTEST", 0))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertNotNull(created);
        assertEquals(SPARK_ADDRESS, created.get("spark_address"));
        assertEquals(IDENTITY_KEY, created.get("identity_public_key"));
        assertEquals("REGTEST", created.get("network"));
        assertEquals(0, created.get("account_index"));
        assertEquals(false, created.get("backup_verified"));
        assertEquals(false, created.get("privacy_enabled"));
        assertEquals(1, sparkWalletRepository.count());

        webTestClient().get().uri("/v1/spark/wallet")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.spark_address").isEqualTo(SPARK_ADDRESS)
                .jsonPath("$.backup_verified").isEqualTo(false);

        // Backup-verified flag.
        webTestClient().post().uri("/v1/spark/backup-verified")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"backup_verified\":true}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.backup_verified").isEqualTo(true);

        // Privacy flag — the authoritative locked-dashboard render source.
        webTestClient().post().uri("/v1/spark/privacy")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"privacy_enabled\":true}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.privacy_enabled").isEqualTo(true);

        webTestClient().get().uri("/v1/spark/wallet")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.backup_verified").isEqualTo(true)
                .jsonPath("$.privacy_enabled").isEqualTo(true);

        // Forget clears metadata; GET is empty again and re-registration is allowed.
        webTestClient().delete().uri("/v1/spark/wallet")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isNoContent();
        assertEquals(0, sparkWalletRepository.count());

        webTestClient().get().uri("/v1/spark/wallet")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .isEmpty();

        webTestClient().post().uri("/v1/spark/wallets")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody(IDENTITY_KEY, SPARK_ADDRESS, "REGTEST", 0))
                .exchange()
                .expectStatus().isCreated();
        assertEquals(1, sparkWalletRepository.count());
    }

    @Test
    @DisplayName("Register is one-wallet-per-user and identity keys are globally unique")
    void register_conflicts() {
        webTestClient().post().uri("/v1/spark/wallets")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody(IDENTITY_KEY, SPARK_ADDRESS, "REGTEST", 0))
                .exchange()
                .expectStatus().isCreated();

        // Same user registering again -> 409.
        webTestClient().post().uri("/v1/spark/wallets")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody(IDENTITY_KEY, SPARK_ADDRESS, "REGTEST", 0))
                .exchange()
                .expectStatus().isEqualTo(409);

        // Another user reusing the same identity key -> 409.
        webTestClient().post().uri("/v1/spark/wallets")
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody(IDENTITY_KEY, SPARK_ADDRESS, "REGTEST", 0))
                .exchange()
                .expectStatus().isEqualTo(409);

        assertEquals(1, sparkWalletRepository.count());
    }

    @Test
    @DisplayName("Owner isolation: another user never sees the owner wallet and forget is scoped")
    void owner_isolation() {
        webTestClient().post().uri("/v1/spark/wallets")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody(IDENTITY_KEY, SPARK_ADDRESS, "REGTEST", 0))
                .exchange()
                .expectStatus().isCreated();

        // Other user sees no wallet.
        webTestClient().get().uri("/v1/spark/wallet")
                .header("Authorization", "Bearer " + otherToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .isEmpty();

        // Other user can register their own wallet with a different identity key.
        String otherKey = "03" + IDENTITY_KEY.substring(2);
        webTestClient().post().uri("/v1/spark/wallets")
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody(otherKey, "sparkrt1q2gkz8d5f4h3j9m7v2b1n6c0x8w4y5z6a7k9t1p4s2e", "REGTEST", 0))
                .exchange()
                .expectStatus().isCreated();
        assertEquals(2, sparkWalletRepository.count());

        // Other user's forget does not touch the owner wallet.
        webTestClient().delete().uri("/v1/spark/wallet")
                .header("Authorization", "Bearer " + otherToken)
                .exchange()
                .expectStatus().isNoContent();
        assertEquals(1, sparkWalletRepository.count());

        webTestClient().get().uri("/v1/spark/wallet")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.identity_public_key").isEqualTo(IDENTITY_KEY);
    }

    @Test
    @DisplayName("Validation: bad identity key / bad network / missing flag are 400")
    void validation_rejects() {
        webTestClient().post().uri("/v1/spark/wallets")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody("04" + IDENTITY_KEY.substring(2), SPARK_ADDRESS, "REGTEST", 0))
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient().post().uri("/v1/spark/wallets")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody(IDENTITY_KEY, SPARK_ADDRESS, "TESTNET", 0))
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient().post().uri("/v1/spark/privacy")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest();

        assertEquals(0, sparkWalletRepository.count());
    }

    @Test
    @DisplayName("Flag sync and forget on a missing wallet behave correctly")
    void missing_wallet_behaviour() {
        // Flags on a missing wallet -> 404.
        webTestClient().post().uri("/v1/spark/privacy")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"privacy_enabled\":true}")
                .exchange()
                .expectStatus().isNotFound();

        webTestClient().post().uri("/v1/spark/backup-verified")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"backup_verified\":true}")
                .exchange()
                .expectStatus().isNotFound();

        // Forget on a missing wallet is an idempotent 204.
        webTestClient().delete().uri("/v1/spark/wallet")
                .header("Authorization", "Bearer " + ownerToken)
                .exchange()
                .expectStatus().isNoContent();
    }

    private String registerAndVerify(String email, String name, String alias) {
        String password = "SecurePass123!";
        webTestClient().post().uri("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registration(name, email, password, alias))
                .exchange()
                .expectStatus().isCreated();

        String code = verificationDataRepository.findById(email).orElseThrow().getCode();
        AuthResponseDTO tokens = webTestClient().post().uri("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verification(email, code))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponseDTO.class)
                .returnResult().getResponseBody();
        assertNotNull(tokens);
        return tokens.getAccessToken();
    }

    private static RegistrationRequestDTO registration(String name, String email, String password, String alias) {
        RegistrationRequestDTO request = new RegistrationRequestDTO();
        request.setName(name);
        request.setEmail(email);
        request.setPassword(password);
        request.setAlias(alias);
        return request;
    }

    private static VerificationRequestDTO verification(String email, String code) {
        VerificationRequestDTO request = new VerificationRequestDTO();
        request.setEmail(email);
        request.setCode(code);
        return request;
    }

    private static String registerBody(String identityKey, String sparkAddress, String network, int accountIndex) {
        return "{\"identity_public_key\":\"" + identityKey
                + "\",\"spark_address\":\"" + sparkAddress
                + "\",\"network\":\"" + network
                + "\",\"account_index\":" + accountIndex + "}";
    }
}
