package com.mailflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank
    private String resetToken; // raw token issued by /verify-otp
    @NotBlank @Size(min = 6)
    private String newPassword;
}
