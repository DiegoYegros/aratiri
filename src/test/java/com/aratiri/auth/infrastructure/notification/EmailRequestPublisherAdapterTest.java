package com.aratiri.auth.infrastructure.notification;

import com.aratiri.auth.application.event.EmailRequestedEvent;
import com.aratiri.auth.application.event.EmailRequestedEvent.EmailType;
import com.aratiri.errors.ApplicationException;
import com.aratiri.infrastructure.messaging.EventPublisher;
import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EmailRequestPublisherAdapterTest {

    private static final JsonMapper JSON_MAPPER = new JsonMapper();

    @Mock
    private EventPublisher eventPublisher;

    private EmailRequestPublisherAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new EmailRequestPublisherAdapter(eventPublisher, "smtp-user", "smtp-pass");
    }

    @Test
    void publishVerificationEmail_publishesWithEmailTopicAndTypePrefixedKey() {
        adapter.publishVerificationEmail("test@example.com", "123456");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(
                org.mockito.ArgumentMatchers.eq(KafkaTopicNames.EMAIL_REQUESTED),
                org.mockito.ArgumentMatchers.eq("VERIFICATION:test@example.com"),
                payloadCaptor.capture());

        EmailRequestedEvent event = JSON_MAPPER.convertValue(payloadCaptor.getValue(), EmailRequestedEvent.class);
        assertEquals(EmailType.VERIFICATION, event.emailType());
        assertEquals("test@example.com", event.to());
        assertEquals("123456", event.code());
    }

    @Test
    void publishPasswordResetEmail_publishesWithEmailTopicAndTypePrefixedKey() {
        adapter.publishPasswordResetEmail("user@example.com", "654321");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publish(
                org.mockito.ArgumentMatchers.eq(KafkaTopicNames.EMAIL_REQUESTED),
                org.mockito.ArgumentMatchers.eq("PASSWORD_RESET:user@example.com"),
                payloadCaptor.capture());

        EmailRequestedEvent event = JSON_MAPPER.convertValue(payloadCaptor.getValue(), EmailRequestedEvent.class);
        assertEquals(EmailType.PASSWORD_RESET, event.emailType());
        assertEquals("user@example.com", event.to());
        assertEquals("654321", event.code());
    }

    @Test
    void publishVerificationEmail_blankUsername_throws503WithoutPublishing() {
        EmailRequestPublisherAdapter unconfigured = new EmailRequestPublisherAdapter(eventPublisher, "", "secret");

        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> unconfigured.publishVerificationEmail("test@example.com", "123456"));

        assertEquals("Email delivery is not configured", ex.getMessage());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getStatus());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void publishPasswordResetEmail_blankPassword_throws503WithoutPublishing() {
        EmailRequestPublisherAdapter unconfigured = new EmailRequestPublisherAdapter(eventPublisher, "smtp-user", "  ");

        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> unconfigured.publishPasswordResetEmail("user@example.com", "654321"));

        assertEquals("Email delivery is not configured", ex.getMessage());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getStatus());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void publishPasswordResetEmail_nullPassword_throws503WithoutPublishing() {
        EmailRequestPublisherAdapter unconfigured = new EmailRequestPublisherAdapter(eventPublisher, "smtp-user", null);

        ApplicationException ex = assertThrows(ApplicationException.class,
                () -> unconfigured.publishPasswordResetEmail("user@example.com", "654321"));

        assertEquals("Email delivery is not configured", ex.getMessage());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getStatus());
        verify(eventPublisher, never()).publish(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }
}
