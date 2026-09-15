package com.mailflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/** Enables @CreatedDate / @LastModifiedDate population on save (timestamps: true equivalent). */
@Configuration
@EnableMongoAuditing
public class MongoAuditConfig {
}
