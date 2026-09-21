package com.hcmute.springlab.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmailServiceImpl implements EmailService {
    private final JavaMailSender mailSender;
    private final String sender;

    public EmailServiceImpl(JavaMailSender mailSender, @Value("${spring.mail.username:}") String sender) {
        this.mailSender = mailSender;
        this.sender = sender;
    }

    @Override public boolean isConfigured() { return StringUtils.hasText(sender); }

    @Override
    public void sendRegistrationOtp(String recipient, String code, long expirationMinutes) {
        sendOtp(recipient, code, expirationMinutes, "SpringLab registration verification",
                "Use this OTP to verify your SpringLab account: ");
    }

    @Override
    public void sendPasswordResetOtp(String recipient, String code, long expirationMinutes) {
        sendOtp(recipient, code, expirationMinutes, "SpringLab password reset",
                "Use this OTP to reset your SpringLab password: ");
    }

    private void sendOtp(String recipient, String code, long expirationMinutes, String subject, String body) {
        if (!isConfigured()) {
            throw new IllegalStateException("SMTP is not configured. Set MAIL_USERNAME and MAIL_PASSWORD first.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender); message.setTo(recipient); message.setSubject(subject);
        message.setText(body + code + "\nIt expires in " + expirationMinutes + " minutes.");
        mailSender.send(message);
    }
}
