package com.aratiri.auth.infrastructure.notification;

import com.aratiri.auth.application.port.out.EmailNotificationPort;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationAdapter implements EmailNotificationPort {

    private final JavaMailSender mailSender;

    public EmailNotificationAdapter(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendVerificationEmail(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Aratiri Account Verification");
        message.setText("Your verification code is: " + code);
        mailSender.send(message);
    }

    @Override
    public void sendPasswordResetEmail(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Aratiri Password Reset Request");
        message.setText("To reset your password, use the following code: " + code);
        mailSender.send(message);
    }
}
