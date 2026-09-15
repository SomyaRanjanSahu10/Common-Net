package com.mailflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Migrated from server/models/ActivityLog.js (Feature 9). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "activityLogs")
public class ActivityLog {
    @Id
    private String id;

    private String user; // User id, nullable

    private String action;  // e.g. LOGIN, SEND_EMAIL, DELETE_USER
    @Builder.Default private String detail = "";
    @Builder.Default private String ip = "";
    @Builder.Default private boolean success = true;

    @CreatedDate
    @Indexed
    private Instant createdAt;
}
