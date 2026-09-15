package com.mailflow.dto;

import com.mailflow.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Never includes the password hash — mirrors `.select('-password')` in the Node routes. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String id;
    private String name;
    private String email;
    private String role;
    private boolean isActive;
    private Instant lastLogin;
    private String avatar;
    private String designation;
    private String department;
    private String phone;
    private String bio;
    private Instant createdAt;

    public static UserResponse from(User u) {
        return UserResponse.builder()
                .id(u.getId()).name(u.getName()).email(u.getEmail()).role(u.getRole())
                .isActive(u.isActive()).lastLogin(u.getLastLogin())
                .avatar(u.getAvatar()).designation(u.getDesignation()).department(u.getDepartment())
                .phone(u.getPhone()).bio(u.getBio()).createdAt(u.getCreatedAt())
                .build();
    }
}
