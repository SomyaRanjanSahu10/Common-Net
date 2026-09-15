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

/** Migrated from server/models/Folder.js. Unique compound index (owner,name)
 *  is created in MongoConfig since Spring Data doesn't support compound
 *  unique indexes via field-level annotations alone. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "folders")
public class Folder {
    @Id
    private String id;

    @Indexed
    private String owner; // User id

    private String name;

    @Builder.Default
    private String color = "#0078d4";

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
}
