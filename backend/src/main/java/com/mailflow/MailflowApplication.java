package com.mailflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Common Net backend — Spring Boot + Spring Data MongoDB + Spring Security (JWT)
 * + Spring Boot Mail (SMTP) + Spring WebSocket.
 *
 * Migrated from the original Node.js / Express / Mongoose / Socket.IO backend.
 * See MIGRATION_NOTES.md at the project root for the full route/feature mapping.
 */
@SpringBootApplication
@EnableScheduling
public class MailflowApplication {
    public static void main(String[] args) {
        SpringApplication.run(MailflowApplication.class, args);
    }
}
