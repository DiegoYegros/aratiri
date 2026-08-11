package com.aratiri.auth;

import com.aratiri.AbstractIntegrationTest;
import com.aratiri.accounts.application.port.out.CurrencyConversionPort;
import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.auth.application.dto.PasswordResetDTOs;
import com.aratiri.auth.application.dto.RegistrationRequestDTO;
import com.aratiri.auth.application.dto.VerificationRequestDTO;
import com.aratiri.auth.application.event.EmailRequestedEvent;
import com.aratiri.auth.application.event.EmailRequestedEvent.EmailType;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.infrastructure.persistence.jpa.repository.VerificationDataRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class EmailRequestIntegrationTest extends AbstractIntegrationTest {

    private static final JsonMapper JSON_MAPPER = new JsonMapper();

    @MockitoBean
    private EmailNotificationPort emailNotificationPort;

    @MockitoBean
    private CurrencyConversionPort currencyConversionPort;

    @MockitoBean
    private LightningAddressPort lightningAddressPort;

    @Autowired
    private VerificationDataRepository verificationDataRepository;

    @Test
    @DisplayName("Register flow publishes an email.requested VERIFICATION record with key and fields")
    void register_publishesEmailRequestedRecord() {
        String email = "email-register@example.com";
        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("usd", BigDecimal.valueOf(50000)));
        when(lightningAddressPort.generateTaprootAddress()).thenReturn("bc1p_email_test");
        doAnswer(invocation -> null).when(emailNotificationPort).sendVerificationEmail(anyString(), anyString());

        webTestClient().post().uri("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registration("Email Register Test", email, "SecurePass123!", "emailregister"))
                .exchange()
                .expectStatus().isCreated();

        ConsumerRecord<String, String> consumerRecord = awaitRecordWithKey("VERIFICATION:" + email);

        assertEquals(EmailType.VERIFICATION, JSON_MAPPER.readValue(consumerRecord.value(), EmailRequestedEvent.class).emailType());
        assertEquals(email, JSON_MAPPER.readValue(consumerRecord.value(), EmailRequestedEvent.class).to());
        assertEquals(verificationDataRepository.findById(email).orElseThrow().getCode(),
                JSON_MAPPER.readValue(consumerRecord.value(), EmailRequestedEvent.class).code());
    }

    @Test
    @DisplayName("Forgot-password flow publishes an email.requested PASSWORD_RESET record for a local user")
    void forgotPassword_publishesPasswordResetEmailRecord() {
        String email = "email-reset@example.com";
        String alias = "emailreset";
        when(currencyConversionPort.getCurrentBtcPrice()).thenReturn(Map.of("usd", BigDecimal.valueOf(50000)));
        when(lightningAddressPort.generateTaprootAddress()).thenReturn("bc1p_email_reset");
        doAnswer(invocation -> null).when(emailNotificationPort).sendVerificationEmail(anyString(), anyString());

        webTestClient().post().uri("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registration("Email Reset Test", email, "SecurePass123!", alias))
                .exchange()
                .expectStatus().isCreated();

        String verificationCode = verificationDataRepository.findById(email).orElseThrow().getCode();
        webTestClient().post().uri("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verification(email, verificationCode))
                .exchange()
                .expectStatus().isOk();

        PasswordResetDTOs.ForgotPasswordRequestDTO forgotRequest = new PasswordResetDTOs.ForgotPasswordRequestDTO();
        forgotRequest.setEmail(email);
        webTestClient().post().uri("/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(forgotRequest)
                .exchange()
                .expectStatus().isOk();

        ConsumerRecord<String, String> consumerRecord = awaitRecordWithKey("PASSWORD_RESET:" + email);

        EmailRequestedEvent event = JSON_MAPPER.readValue(consumerRecord.value(), EmailRequestedEvent.class);
        assertEquals(EmailType.PASSWORD_RESET, event.emailType());
        assertEquals(email, event.to());
        assertEquals(6, event.code().length());
    }

    private ConsumerRecord<String, String> awaitRecordWithKey(String key) {
        List<ConsumerRecord<String, String>> records =
                pollKafkaRecords(KafkaTopicNames.EMAIL_REQUESTED, Duration.ofSeconds(15));
        return records.stream()
                .filter(candidate -> key.equals(candidate.key()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no email.requested record with key " + key));
    }

    private RegistrationRequestDTO registration(String name, String email, String password, String alias) {
        RegistrationRequestDTO request = new RegistrationRequestDTO();
        request.setName(name);
        request.setEmail(email);
        request.setPassword(password);
        request.setAlias(alias);
        return request;
    }

    private VerificationRequestDTO verification(String email, String code) {
        VerificationRequestDTO request = new VerificationRequestDTO();
        request.setEmail(email);
        request.setCode(code);
        return request;
    }
}
