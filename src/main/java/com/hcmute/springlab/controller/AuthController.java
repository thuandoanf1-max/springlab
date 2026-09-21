package com.hcmute.springlab.controller;

import com.hcmute.springlab.dto.RegisterRequest;
import com.hcmute.springlab.dto.ForgotPasswordRequest;
import com.hcmute.springlab.dto.ResetPasswordRequest;
import com.hcmute.springlab.dto.VerifyOtpRequest;
import com.hcmute.springlab.service.PasswordResetService;
import com.hcmute.springlab.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;
    public AuthController(RegistrationService registrationService, PasswordResetService passwordResetService) {
        this.registrationService = registrationService;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/login")
    public String showLoginForm() {
        return "login";
    }

    @GetMapping("/register") public String registerForm(Model model) { model.addAttribute("registerRequest", new RegisterRequest()); return "register"; }
    @PostMapping("/register") public String register(@Valid @ModelAttribute RegisterRequest request, BindingResult result, RedirectAttributes redirect) {
        if (!java.util.Objects.equals(request.getPassword(), request.getConfirmPassword())) result.rejectValue("confirmPassword", "mismatch", "Password confirmation does not match.");
        if (result.hasErrors()) return "register";
        try { registrationService.register(request); redirect.addFlashAttribute("message", "OTP has been sent. Verify your account to log in."); return "redirect:/verify-otp?email=" + request.getEmail(); }
        catch (IllegalArgumentException | IllegalStateException exception) { result.reject("register", exception.getMessage()); return "register"; }
    }
    @GetMapping("/verify-otp") public String verifyForm(@RequestParam(required = false) String email, Model model) { VerifyOtpRequest request = new VerifyOtpRequest(); request.setEmail(email); model.addAttribute("verifyOtpRequest", request); return "verify-otp"; }
    @PostMapping("/verify-otp") public String verify(@Valid @ModelAttribute VerifyOtpRequest request, BindingResult result, RedirectAttributes redirect) {
        if (result.hasErrors()) return "verify-otp";
        try { registrationService.verify(request.getEmail(), request.getCode()); redirect.addFlashAttribute("success", "Account verified. You can now log in."); return "redirect:/login"; }
        catch (IllegalArgumentException exception) { result.reject("otp", exception.getMessage()); return "verify-otp"; }
    }
    @PostMapping("/register/resend-otp") public String resend(@RequestParam String email, RedirectAttributes redirect) {
        try { registrationService.resend(email); redirect.addFlashAttribute("message", "A new OTP has been sent."); }
        catch (IllegalArgumentException | IllegalStateException exception) { redirect.addFlashAttribute("error", exception.getMessage()); }
        return "redirect:/verify-otp?email=" + email;
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm(Model model) {
        model.addAttribute("forgotPasswordRequest", new ForgotPasswordRequest());
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@Valid @ModelAttribute ForgotPasswordRequest request, BindingResult result,
                                 RedirectAttributes redirect) {
        if (result.hasErrors()) return "forgot-password";
        try {
            passwordResetService.requestOtp(request.getEmail());
        } catch (RuntimeException exception) {
            // Keep the response neutral so account existence is not disclosed.
        }
        redirect.addFlashAttribute("message", "If the email exists, a verification code has been sent.");
        redirect.addAttribute("email", request.getEmail());
        return "redirect:/reset-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam(required = false) String email, Model model) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail(email);
        model.addAttribute("resetPasswordRequest", request);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@Valid @ModelAttribute ResetPasswordRequest request, BindingResult result) {
        if (!java.util.Objects.equals(request.getPassword(), request.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "mismatch", "Password confirmation does not match.");
        }
        if (result.hasErrors()) return "reset-password";
        try {
            passwordResetService.resetPassword(request);
            return "redirect:/login?resetSuccess";
        } catch (IllegalArgumentException exception) {
            result.reject("reset", exception.getMessage());
            return "reset-password";
        }
    }

    @PostMapping("/forgot-password/resend-otp")
    public String resendResetOtp(@RequestParam String email, RedirectAttributes redirect) {
        try {
            passwordResetService.resendOtp(email);
            redirect.addFlashAttribute("message", "If the email exists, a new verification code has been sent.");
        } catch (RuntimeException exception) {
            redirect.addFlashAttribute("error", "The code could not be sent yet. Please wait and try again.");
        }
        redirect.addAttribute("email", email);
        return "redirect:/reset-password";
    }
}
