package com.mailflow.dto;

import lombok.Data;

@Data
public class AddAccountRequest {
    private String email;
    private String password;
    private String label;
}
