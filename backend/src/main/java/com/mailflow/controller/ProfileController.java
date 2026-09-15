package com.mailflow.controller;

import com.mailflow.dto.ProfileUpdateRequest;
import com.mailflow.dto.UserResponse;
import com.mailflow.exception.ApiException;
import com.mailflow.security.UserPrincipal;
import com.mailflow.service.ProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** Migrated from server/routes/profile.js. */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public Map<String, Object> myProfile(@AuthenticationPrincipal UserPrincipal me) {
        return Map.of("user", profileService.myProfile(me.getUser()));
    }

    @GetMapping("/{userId}")
    public Map<String, Object> publicProfile(@PathVariable String userId) {
        return Map.of("user", profileService.publicProfile(userId));
    }

    @PutMapping
    public Map<String, Object> update(@AuthenticationPrincipal UserPrincipal me, @RequestBody ProfileUpdateRequest req) {
        UserResponse updated = profileService.update(me.getUser(), req.getName(), req.getDesignation(),
                req.getDepartment(), req.getPhone(), req.getBio());
        return Map.of("message", "Profile updated", "user", updated);
    }

    @PostMapping(value = "/avatar", consumes = "multipart/form-data")
    public Map<String, Object> uploadAvatar(@AuthenticationPrincipal UserPrincipal me,
                                             @RequestParam("avatar") MultipartFile avatar) {
        if (avatar == null || avatar.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "No image uploaded");
        String avatarUrl = profileService.updateAvatar(me.getUser(), avatar);
        return Map.of("message", "Avatar updated", "avatar", avatarUrl, "user", UserResponse.from(me.getUser()));
    }
}
