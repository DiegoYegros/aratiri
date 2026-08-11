package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.auth.application.event.EmailRequestedEvent;
import com.aratiri.auth.application.event.EmailRequestedEvent.EmailType;
import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailConsumerTest {

    @Mock
    private EmailNotificationPort emailNotificationPort;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private Acknowledgment acknowledgment;

    private EmailConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new EmailConsumer(emailNotificationPort, jsonMapper);
    }

    @Test
    void handleEmailRequested_verification_sendsAndAcknowledges() throws Exception {
        when(jsonMapper.readValue("{msg}", EmailRequestedEvent.class))
                .thenReturn(new EmailRequestedEvent(EmailType.VERIFICATION, "test@example.com", "123456"));

        consumer.handleEmailRequested("{msg}", KafkaTopicNames.EMAIL_REQUESTED, 0, 0L, acknowledgment);

        verify(emailNotificationPort).sendVerificationEmail("test@example.com", "123456");
        verify(emailNotificationPort, never()).sendPasswordResetEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleEmailRequested_passwordReset_sendsAndAcknowledges() throws Exception {
        when(jsonMapper.readValue("{msg}", EmailRequestedEvent.class))
                .thenReturn(new EmailRequestedEvent(EmailType.PASSWORD_RESET, "test@example.com", "654321"));

        consumer.handleEmailRequested("{msg}", KafkaTopicNames.EMAIL_REQUESTED, 0, 0L, acknowledgment);

        verify(emailNotificationPort).sendPasswordResetEmail("test@example.com", "654321");
        verify(emailNotificationPort, never()).sendVerificationEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleEmailRequested_sendFailure_throwsWithoutAcknowledging() throws Exception {
        when(jsonMapper.readValue("{msg}", EmailRequestedEvent.class))
                .thenReturn(new EmailRequestedEvent(EmailType.VERIFICATION, "test@example.com", "123456"));
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp down"))
                .when(emailNotificationPort).sendVerificationEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());

        assertThrows(
                IllegalStateException.class,
                () -> consumer.handleEmailRequested("{msg}", KafkaTopicNames.EMAIL_REQUESTED, 0, 0L, acknowledgment)
        );

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleEmailRequested_malformedJson_throwsWithoutAcknowledging() throws Exception {
        when(jsonMapper.readValue("{poison", EmailRequestedEvent.class))
                .thenThrow(new IllegalArgumentException("bad payload"));

        assertThrows(
                IllegalArgumentException.class,
                () -> consumer.handleEmailRequested("{poison", KafkaTopicNames.EMAIL_REQUESTED, 0, 0L, acknowledgment)
        );

        verify(emailNotificationPort, never()).sendVerificationEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(emailNotificationPort, never()).sendPasswordResetEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleFailedEmail_logsWithoutRethrowing() {
        assertDoesNotThrow(() -> consumer.handleFailedEmail(
                "{msg}", KafkaTopicNames.EMAIL_REQUESTED, "boom"));
    }
}
