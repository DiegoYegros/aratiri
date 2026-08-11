package com.aratiri.auth.application.port.out;

public interface EmailRequestPublisher {

    void publishVerificationEmail(String to, String code);

    void publishPasswordResetEmail(String to, String code);
}
