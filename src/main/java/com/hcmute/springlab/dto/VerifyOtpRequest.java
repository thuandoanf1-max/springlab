package com.hcmute.springlab.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyOtpRequest {
    @NotBlank @Email private String email;
    @NotBlank @Pattern(regexp = "\\d{6}", message = "OTP must contain 6 digits") private String code;
}
