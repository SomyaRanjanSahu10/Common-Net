package com.mailflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Migrated from server/models/AdminLog.js — audit trail of admin actions. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "adminLogs")
public class AdminLog {
    @Id
    private String id;

    private String admin; // User id (must be admin)
    private String action; // e.g. 'DELETE_USER', 'VIEW_EMAILS'
    @Builder.Default private String target = "";
    @Builder.Default private String detail = "";
    @Builder.Default private String ip = "";

    @CreatedDate
    private Instant createdAt;
}
