package com.aratiri.auth.infrastructure.notification;

import com.aratiri.auth.application.port.out.EmailNotificationPort;
import com.aratiri.errors.ApplicationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationAdapter implements EmailNotificationPort {

    private final JavaMailSender mailSender;
    private final String username;
    private final String password;

    public EmailNotificationAdapter(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password
    ) {
        this.mailSender = mailSender;
        this.username = username;
        this.password = password;
    }

    @Override
    public void sendVerificationEmail(String to, String code) {
        requireMailConfigured();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Aratiri Account Verification");
        message.setText("Your verification code is: " + code);
        mailSender.send(message);
    }

    @Override
    public void sendPasswordResetEmail(String to, String code) {
        requireMailConfigured();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Aratiri Password Reset Request");
        message.setText("To reset your password, use the following code: " + code);
        mailSender.send(message);
    }

    private void requireMailConfigured() {
        if (isBlank(username) || isBlank(password)) {
            throw new ApplicationException(
                    "Email delivery is not configured",
                    HttpStatus.SERVICE_UNAVAILABLE.value()
            );
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
