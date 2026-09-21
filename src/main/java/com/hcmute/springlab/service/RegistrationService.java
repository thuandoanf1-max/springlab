package com.hcmute.springlab.service;

import com.hcmute.springlab.dto.RegisterRequest;
import com.hcmute.springlab.entity.OtpToken;
import com.hcmute.springlab.entity.OtpType;
import com.hcmute.springlab.entity.User;
import com.hcmute.springlab.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class RegistrationService {
    private final UserRepository userRepository;
    private final UserService userService;
    private final OtpService otpService;
    private final EmailService emailService;

    public RegistrationService(UserRepository userRepository, UserService userService, OtpService otpService, EmailService emailService) {
        this.userRepository = userRepository; this.userService = userService; this.otpService = otpService; this.emailService = emailService;
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) throw new IllegalArgumentException("Password confirmation does not match.");
        if (userRepository.existsByUsernameIgnoreCase(request.getUsername())) throw new IllegalArgumentException("Username already exists.");
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) throw new IllegalArgumentException("Email already exists. Please verify the existing account or resend its OTP.");
        User user = new User(); user.setUsername(request.getUsername().trim()); user.setFullname(request.getFullname().trim());
        user.setEmail(request.getEmail().trim()); user.setPassword(request.getPassword()); user.setRole("USER"); user.setEnabled(false);
        user = userService.save(user);
        OtpToken otp = otpService.create(user, OtpType.REGISTER, false);
        emailService.sendRegistrationOtp(user.getEmail(), otp.getCode(), otpService.expirationMinutes());
    }

    @Transactional
    public void verify(String email, String code) {
        User user = userRepository.findFirstByUsernameIgnoreCaseOrEmailIgnoreCase(email, email)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));
        OtpToken otp = otpService.current(user, OtpType.REGISTER);
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) throw new IllegalArgumentException("OTP has expired. Please request a new OTP.");
        if (!otp.getCode().equals(code)) throw new IllegalArgumentException("OTP is invalid.");
        otp.setUsed(true); user.setEnabled(true);
        userRepository.save(user);
    }

    @Transactional
    public void resend(String email) {
        User user = userRepository.findFirstByUsernameIgnoreCaseOrEmailIgnoreCase(email, email)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));
        if (Boolean.TRUE.equals(user.getEnabled())) throw new IllegalArgumentException("This account has already been verified.");
        OtpToken otp = otpService.create(user, OtpType.REGISTER, true);
        emailService.sendRegistrationOtp(user.getEmail(), otp.getCode(), otpService.expirationMinutes());
    }
}
