package com.hcmute.springlab.service;

public interface EmailService {
    void sendRegistrationOtp(String recipient, String code, long expirationMinutes);
    void sendPasswordResetOtp(String recipient, String code, long expirationMinutes);
    boolean isConfigured();
}
