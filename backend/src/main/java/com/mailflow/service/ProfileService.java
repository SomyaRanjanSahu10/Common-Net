package com.mailflow.service;

import com.mailflow.dto.UserResponse;
import com.mailflow.exception.ApiException;
import com.mailflow.model.User;
import com.mailflow.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Path;

/** Direct port of server/routes/profile.js. */
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    public ProfileService(UserRepository userRepository, FileStorageService fileStorageService) {
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
    }

    public UserResponse myProfile(User me) {
        return UserResponse.from(me);
    }

    /** Public profile card — mirrors .select('name email avatar designation department phone'). */
    public java.util.Map<String, Object> publicProfile(String userId) {
        User u = userRepository.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        return java.util.Map.of(
                "name", u.getName(), "email", u.getEmail(), "avatar", u.getAvatar(),
                "designation", u.getDesignation(), "department", u.getDepartment(), "phone", u.getPhone()
        );
    }

    public UserResponse update(User me, String name, String designation, String department, String phone, String bio) {
        if (name != null) me.setName(name);
        if (designation != null) me.setDesignation(designation);
        if (department != null) me.setDepartment(department);
        if (phone != null) me.setPhone(phone);
        if (bio != null) me.setBio(bio);
        User saved = userRepository.save(me);
        return UserResponse.from(saved);
    }

    public String updateAvatar(User me, MultipartFile file) {
        // Delete old avatar file if present, mirroring the Node route's fs.unlinkSync cleanup.
        if (me.getAvatar() != null && me.getAvatar().contains("uploads/avatars")) {
            String filename = me.getAvatar().substring(me.getAvatar().lastIndexOf('/') + 1);
            Path oldPath = fileStorageService.avatarsPath().resolve(filename);
            fileStorageService.deleteIfExists(oldPath);
        }
        FileStorageService.StoredFile stored = fileStorageService.storeAvatar(file, me.getId());
        String avatarUrl = "/uploads/avatars/" + stored.filename();
        me.setAvatar(avatarUrl);
        userRepository.save(me);
        return avatarUrl;
    }
}
