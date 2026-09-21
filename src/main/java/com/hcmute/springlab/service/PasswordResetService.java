package com.hcmute.springlab.service;

import com.hcmute.springlab.dto.ResetPasswordRequest;
import com.hcmute.springlab.entity.OtpToken;
import com.hcmute.springlab.entity.OtpType;
import com.hcmute.springlab.entity.User;
import com.hcmute.springlab.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PasswordResetService {
    private final UserRepository userRepository;
    private final OtpService otpService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(UserRepository userRepository, OtpService otpService,
                                EmailService emailService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void requestOtp(String email) {
        Optional<User> user = userRepository.findFirstByUsernameIgnoreCaseOrEmailIgnoreCase(email, email);
        if (user.isEmpty()) {
            return;
        }
        sendNewOtp(user.get(), true);
    }

    @Transactional
    public void resendOtp(String email) {
        Optional<User> user = userRepository.findFirstByUsernameIgnoreCaseOrEmailIgnoreCase(email, email);
        if (user.isEmpty()) {
            return;
        }
        sendNewOtp(user.get(), true);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!java.util.Objects.equals(request.getPassword(), request.getConfirmPassword())) {
            throw new IllegalArgumentException("Password confirmation does not match.");
        }
        User user = userRepository.findFirstByUsernameIgnoreCaseOrEmailIgnoreCase(request.getEmail(), request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset request."));
        OtpToken otp = otpService.current(user, OtpType.PASSWORD_RESET);
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("OTP has expired. Please request a new OTP.");
        }
        if (!otp.getCode().equals(request.getOtp())) {
            throw new IllegalArgumentException("OTP is invalid.");
        }
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        otp.setUsed(true);
        userRepository.save(user);
    }

    private void sendNewOtp(User user, boolean enforceCooldown) {
        OtpToken otp = otpService.create(user, OtpType.PASSWORD_RESET, enforceCooldown);
        emailService.sendPasswordResetOtp(user.getEmail(), otp.getCode(), otpService.expirationMinutes());
    }
}
