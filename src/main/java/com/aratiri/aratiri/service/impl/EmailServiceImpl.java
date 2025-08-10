package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Override
    public void sendVerificationEmail(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Aratiri Account Verification");
        message.setText("Your verification code is: " + code);
        mailSender.send(message);
    }

    @Override
    public void sendPasswordResetEmail(String to, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Aratiri Password Reset Request");
        message.setText("To reset your password, use the following token: " + token);
        mailSender.send(message);
    }
}
