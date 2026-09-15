package com.mailflow.dto;

import lombok.Data;

@Data
public class ProfileUpdateRequest {
    private String name;
    private String designation;
    private String department;
    private String phone;
    private String bio;
}
