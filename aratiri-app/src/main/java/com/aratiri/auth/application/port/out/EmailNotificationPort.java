package com.aratiri.auth.application.port.out;

public interface EmailNotificationPort {

    void sendVerificationEmail(String to, String code);

    void sendPasswordResetEmail(String to, String code);
}
