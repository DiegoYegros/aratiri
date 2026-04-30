package com.aratiri.auth.infrastructure.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailNotificationAdapterTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailNotificationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new EmailNotificationAdapter(mailSender);
    }

    @Test
    void sendVerificationEmail() {
        adapter.sendVerificationEmail("test@example.com", "123456");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals("test@example.com", message.getTo()[0]);
        assertEquals("Aratiri Account Verification", message.getSubject());
        assertTrue(message.getText().contains("123456"));
    }

    @Test
    void sendPasswordResetEmail() {
        adapter.sendPasswordResetEmail("user@example.com", "654321");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals("user@example.com", message.getTo()[0]);
        assertEquals("Aratiri Password Reset Request", message.getSubject());
        assertTrue(message.getText().contains("654321"));
    }
}
