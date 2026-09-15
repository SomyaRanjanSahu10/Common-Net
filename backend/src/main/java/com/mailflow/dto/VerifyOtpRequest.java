package com.mailflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyOtpRequest {
    @NotBlank
    private String email;
    @NotBlank
    private String otp;
}
