package com.mailflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Migrated from server/models/Email.js. Mongoose's compound text index on
 * {subject, body} is recreated in MongoConfig via an IndexOperations call
 * (Spring Data annotations don't support compound text indexes directly).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "emails")
public class Email {

    @Id
    private String id;

    @Indexed
    private String sender;   // User id

    @Indexed
    private String receiver; // User id, nullable

    @Builder.Default
    private String toEmail = "";

    private String subject;
    @Builder.Default
    private String body = "";
    @Builder.Default
    private String htmlBody = "";

    // ── Status flags ────────────────────────────────────────────────────
    @Builder.Default private boolean isRead = false;
    @Builder.Default private boolean isStarred = false;
    @Builder.Default private boolean isImportant = false;
    @Builder.Default private boolean isDraft = false;
    @Builder.Default private boolean isArchived = false;
    @Builder.Default private boolean isSent = false;

    // ── Trash system (Feature 1) ───────────────────────────────────────
    @Builder.Default private boolean trashedBySender = false;
    @Builder.Default private boolean trashedByReceiver = false;
    @Indexed(sparse = true)
    private Instant trashedAt;
    @Builder.Default private boolean deletedBySender = false;
    @Builder.Default private boolean deletedByReceiver = false;

    // ── Recall (Feature 7) ─────────────────────────────────────────────
    @Builder.Default private boolean isRecalled = false;
    private Instant recalledAt;

    // ── Scheduling ──────────────────────────────────────────────────────
    private Instant scheduledTime;
    @Builder.Default
    private String meetingLink = "";

    // ── Recipients ──────────────────────────────────────────────────────
    @Builder.Default
    private List<String> cc = new ArrayList<>();
    @Builder.Default
    private List<String> bcc = new ArrayList<>();

    // ── Relations ────────────────────────────────────────────────────────
    private String folder; // Folder id
    @Builder.Default
    private List<Mention> mentions = new ArrayList<>();
    @Builder.Default
    private List<Attachment> attachments = new ArrayList<>();

    // ── Tracking ─────────────────────────────────────────────────────────
    private Instant deliveredAt;
    private Instant openedAt;
    @Builder.Default private boolean smtpSent = false;
    private String smtpError;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attachment {
        private String id;
        private String originalName;
        private String filename;
        private String mimetype;
        private long size;
        private String path;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Mention {
        private String user; // User id
        private String email;
        private String name;
    }
}
