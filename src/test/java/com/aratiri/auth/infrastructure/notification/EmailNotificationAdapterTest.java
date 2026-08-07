package com.aratiri.auth.infrastructure.notification;

import com.aratiri.errors.ApplicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EmailNotificationAdapterTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendVerificationEmail() {
        EmailNotificationAdapter adapter = new EmailNotificationAdapter(mailSender, "user@example.com", "secret");

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
        EmailNotificationAdapter adapter = new EmailNotificationAdapter(mailSender, "user@example.com", "secret");

        adapter.sendPasswordResetEmail("user@example.com", "654321");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertEquals("user@example.com", message.getTo()[0]);
        assertEquals("Aratiri Password Reset Request", message.getSubject());
        assertTrue(message.getText().contains("654321"));
    }

    @Test
    void sendVerificationEmail_blankUsername_doesNotSend() {
        EmailNotificationAdapter adapter = new EmailNotificationAdapter(mailSender, "", "secret");

        ApplicationException ex = assertThrows(
                ApplicationException.class,
                () -> adapter.sendVerificationEmail("test@example.com", "123456")
        );

        assertEquals("Email delivery is not configured", ex.getMessage());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getStatus());
        verifyNoInteractions(mailSender);
    }

    @Test
    void sendPasswordResetEmail_blankUsername_doesNotSend() {
        EmailNotificationAdapter adapter = new EmailNotificationAdapter(mailSender, "   ", "secret");

        ApplicationException ex = assertThrows(
                ApplicationException.class,
                () -> adapter.sendPasswordResetEmail("user@example.com", "654321")
        );

        assertEquals("Email delivery is not configured", ex.getMessage());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getStatus());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendVerificationEmail_blankPassword_doesNotSend() {
        EmailNotificationAdapter adapter = new EmailNotificationAdapter(mailSender, "user@example.com", "");

        ApplicationException ex = assertThrows(
                ApplicationException.class,
                () -> adapter.sendVerificationEmail("test@example.com", "123456")
        );

        assertEquals("Email delivery is not configured", ex.getMessage());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getStatus());
        verifyNoInteractions(mailSender);
    }

    @Test
    void sendPasswordResetEmail_blankPassword_doesNotSend() {
        EmailNotificationAdapter adapter = new EmailNotificationAdapter(mailSender, "user@example.com", null);

        ApplicationException ex = assertThrows(
                ApplicationException.class,
                () -> adapter.sendPasswordResetEmail("user@example.com", "654321")
        );

        assertEquals("Email delivery is not configured", ex.getMessage());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getStatus());
        verifyNoInteractions(mailSender);
    }

    @Test
    void bothCredentialsSet_sendInvoked() {
        EmailNotificationAdapter adapter = new EmailNotificationAdapter(mailSender, "smtp-user", "smtp-pass");

        adapter.sendVerificationEmail("a@b.com", "111111");
        adapter.sendPasswordResetEmail("a@b.com", "222222");

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }
}
