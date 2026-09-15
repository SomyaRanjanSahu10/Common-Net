package com.mailflow.dto;

import lombok.Data;

@Data
public class SignatureRequest {
    private String name;
    private String designation;
    private String company;
    private String phone;
    private String regards;
    private String htmlContent;
    private Boolean isEnabled;
}
