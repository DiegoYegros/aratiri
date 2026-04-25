package com.aratiri.invoices;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.auth.application.dto.AuthResponseDTO;
import com.aratiri.auth.application.dto.RegistrationRequestDTO;
import com.aratiri.auth.application.dto.VerificationRequestDTO;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.infrastructure.persistence.jpa.repository.AccountRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.LightningInvoiceRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.VerificationDataRepository;
import com.aratiri.invoices.application.dto.GenerateInvoiceDTO;
import com.aratiri.invoices.application.dto.GenerateInvoiceRequestDTO;
import com.aratiri.invoices.application.port.out.LightningNodePort;
import com.aratiri.invoices.domain.DecodedLightningInvoice;
import com.aratiri.invoices.domain.LightningInvoiceCreation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class InvoicesIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private EmailNotificationPort emailNotificationPort;

    @MockitoBean
    private CurrencyConversionPort currencyConversionPort;

    @MockitoBean
    private LightningAddressPort lightningAddressPort;

    @MockitoBean
    private LightningNodePort lightningNodePort;

    @Autowired
    private VerificationDataRepository verificationDataRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LightningInvoiceRepository lightningInvoiceRepository;

    private String accessToken;

    @BeforeEach
    void authenticateUser() {
        String email = "invoices-test@example.com";
        String password = "SecurePass123!";
        String name = "Invoices Test";
        String alias = "invoicestest";

        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("usd", BigDecimal.valueOf(50000)));
        when(lightningAddressPort.generateTaprootAddress()).thenReturn("bc1p_test_address");
        doAnswer(invocation -> null).when(emailNotificationPort).sendVerificationEmail(anyString(), anyString());

        when(lightningNodePort.createInvoice(any(Long.class), anyString(), any(byte[].class), any(byte[].class)))
                .thenAnswer(invocation -> {
                    long amount = invocation.getArgument(0);
                    byte[] hash = invocation.getArgument(3);
                    String paymentHash = bytesToHex(hash);
                    return new LightningInvoiceCreation("lnbc" + amount + "test" + paymentHash, paymentHash, 3600);
                });

        when(lightningNodePort.decodePaymentRequest(anyString()))
                .thenReturn(new DecodedLightningInvoice(
                        "test-payment-hash",
                        1000,
                        "Test invoice",
                        null,
                        3600,
                        "test-destination",
                        40,
                        "test-payment-addr",
                        Instant.now(),
                        null,
                        List.of()
                ));

        webTestClient().post().uri("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRegistrationRequest(name, email, password, alias))
                .exchange()
                .expectStatus().isCreated();

        String verificationCode = verificationDataRepository.findById(email)
                .orElseThrow()
                .getCode();

        AuthResponseDTO verifiedTokens = webTestClient().post().uri("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createVerificationRequest(email, verificationCode))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponseDTO.class)
                .returnResult().getResponseBody();

        assertNotNull(verifiedTokens);
        this.accessToken = verifiedTokens.getAccessToken();
    }

    @Test
    @DisplayName("Generate invoice persists with payment hash")
    void generateInvoice_persists_with_payment_hash() {
        GenerateInvoiceRequestDTO request = new GenerateInvoiceRequestDTO();
        request.setSatsAmount(1000);
        request.setMemo("Test invoice");

        GenerateInvoiceDTO response = webTestClient().post().uri("/v1/invoices")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(GenerateInvoiceDTO.class)
                .returnResult().getResponseBody();

        assertNotNull(response);
        assertNotNull(response.getPaymentRequest());

        var savedInvoices = lightningInvoiceRepository.findAll();
        assertEquals(1, savedInvoices.size());
        assertNotNull(savedInvoices.get(0).getPaymentHash());
        assertEquals(1000L, savedInvoices.get(0).getAmountSats());
        assertEquals("Test invoice", savedInvoices.get(0).getMemo());
    }

    @Test
    @DisplayName("Decode invoice returns correct metadata")
    void decodeInvoice_returns_correct_metadata() {
        webTestClient().get().uri("/v1/invoices/invoice/decode/lnbc1000test")
                .header("Authorization", "Bearer " + accessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.payment_hash").isEqualTo("test-payment-hash")
                .jsonPath("$.num_satoshis").isEqualTo(1000);
    }

    @Test
    @DisplayName("Unauthenticated request to invoices endpoint returns 401")
    void unauthenticated_request_returns_401() {
        GenerateInvoiceRequestDTO request = new GenerateInvoiceRequestDTO();
        request.setSatsAmount(1000);
        request.setMemo("Test");

        webTestClient().post().uri("/v1/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private RegistrationRequestDTO createRegistrationRequest(String name, String email, String password, String alias) {
        RegistrationRequestDTO request = new RegistrationRequestDTO();
        request.setName(name);
        request.setEmail(email);
        request.setPassword(password);
        request.setAlias(alias);
        return request;
    }

    private VerificationRequestDTO createVerificationRequest(String email, String code) {
        VerificationRequestDTO request = new VerificationRequestDTO();
        request.setEmail(email);
        request.setCode(code);
        return request;
    }
}
