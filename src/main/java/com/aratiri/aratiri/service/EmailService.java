package com.aratiri.aratiri.service;

public interface EmailService {
    void sendVerificationEmail(String to, String code);
}