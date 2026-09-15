package com.mailflow.service;

import com.mailflow.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * Replaces server/middleware/upload.js (Multer disk storage + file-type
 * allowlist + size limits) for email attachments, and the inline avatar
 * multer config in server/routes/profile.js. Files are written to
 * ${mailflow.uploads.dir} (default ./uploads) and served back via
 * StaticResourceConfig at /uploads/**, matching express.static('/uploads').
 */
@Service
public class FileStorageService {

    @Value("${mailflow.uploads.dir:uploads}")
    private String uploadsDir;

    private static final Set<String> ALLOWED_ATTACHMENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain", "text/csv",
            "application/zip", "application/x-zip-compressed", "application/octet-stream"
    );

    private static final long MAX_ATTACHMENT_SIZE = 10L * 1024 * 1024; // 10MB, matches Multer limits
    private static final long MAX_AVATAR_SIZE = 5L * 1024 * 1024;      // 5MB

    public record StoredFile(String originalName, String filename, String mimetype, long size, String path) {}

    public Path uploadsPath() {
        return Paths.get(uploadsDir).toAbsolutePath().normalize();
    }

    public Path avatarsPath() {
        return uploadsPath().resolve("avatars");
    }

    public void ensureDirs() {
        try {
            Files.createDirectories(uploadsPath());
            Files.createDirectories(avatarsPath());
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directories", e);
        }
    }

    /** Mirrors upload.js's fileFilter + storage.filename (uuid + original extension). */
    public StoredFile storeAttachment(MultipartFile file) {
        if (file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Files must be under 10 MB");
        }
        if (!ALLOWED_ATTACHMENT_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "File type \"" + file.getContentType() + "\" is not allowed");
        }
        return store(file, uploadsPath(), "");
    }

    /** Mirrors the avatar multer config in profile.js: images only, 5MB limit, avatar_<uid>_<uuid> naming. */
    public StoredFile storeAvatar(MultipartFile file, String userId) {
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Avatar must be under 5 MB");
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Images only");
        }
        return store(file, avatarsPath(), "avatar_" + userId + "_");
    }

    private StoredFile store(MultipartFile file, Path dir, String prefix) {
        ensureDirs();
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase() : "";
        String filename = prefix + UUID.randomUUID() + ext;
        Path target = dir.resolve(filename);
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error storing file");
        }
        return new StoredFile(original, filename, file.getContentType(), file.getSize(), target.toString());
    }

    public void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
