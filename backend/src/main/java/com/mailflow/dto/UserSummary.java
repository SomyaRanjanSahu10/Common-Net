package com.mailflow.dto;

import com.mailflow.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Minimal populated-user shape, mirrors .populate('sender','name email avatar designation'). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummary {
    private String id;
    private String name;
    private String email;
    private String avatar;
    private String designation;

    public static UserSummary from(User u) {
        if (u == null) return null;
        return UserSummary.builder()
                .id(u.getId()).name(u.getName()).email(u.getEmail())
                .avatar(u.getAvatar()).designation(u.getDesignation())
                .build();
    }
}
