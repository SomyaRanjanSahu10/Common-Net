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

/** Migrated from server/models/Signature.js (Feature 2). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "signatures")
public class Signature {
    @Id
    private String id;

    @Indexed(unique = true)
    private String user; // User id

    @Builder.Default private String name = "";
    @Builder.Default private String designation = "";
    @Builder.Default private String company = "";
    @Builder.Default private String phone = "";
    @Builder.Default private String regards = "Best Regards";
    @Builder.Default private String htmlContent = "";
    @Builder.Default private boolean isEnabled = true;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
}
