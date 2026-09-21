package com.hcmute.springlab.service;

import com.hcmute.springlab.entity.OtpToken;
import com.hcmute.springlab.entity.OtpType;
import com.hcmute.springlab.entity.User;
import com.hcmute.springlab.repository.OtpTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class OtpService {
    private final OtpTokenRepository repository;
    private final long expirationMinutes;
    private final long resendCooldownSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpService(OtpTokenRepository repository,
                      @Value("${app.otp.expiration-minutes:5}") long expirationMinutes,
                      @Value("${app.otp.resend-cooldown-seconds:60}") long resendCooldownSeconds) {
        this.repository = repository; this.expirationMinutes = expirationMinutes; this.resendCooldownSeconds = resendCooldownSeconds;
    }

    public OtpToken create(User user, OtpType type, boolean enforceCooldown) {
        repository.findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(user, type).ifPresent(previous -> {
            if (enforceCooldown && previous.getCreatedAt().plusSeconds(resendCooldownSeconds).isAfter(LocalDateTime.now())) {
                throw new IllegalStateException("Please wait before requesting another OTP.");
            }
            previous.setUsed(true);
            repository.save(previous);
        });
        // Do not reissue an earlier code for this user/purpose: old emails must stay invalid.
        String code;
        do {
            code = "%06d".formatted(secureRandom.nextInt(1_000_000));
        } while (repository.existsByUserAndTypeAndCode(user, type, code));
        OtpToken token = new OtpToken(); token.setUser(user); token.setType(type); token.setCode(code);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes)); token.setUsed(false);
        return repository.save(token);
    }

    public OtpToken current(User user, OtpType type) {
        return repository.findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(user, type)
                .orElseThrow(() -> new IllegalArgumentException("OTP is invalid or has already been used."));
    }
    public long expirationMinutes() { return expirationMinutes; }
}
