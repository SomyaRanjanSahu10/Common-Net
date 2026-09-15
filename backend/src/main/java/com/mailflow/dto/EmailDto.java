package com.mailflow.dto;

import com.mailflow.model.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** Response shape for an Email, with sender/receiver populated as UserSummary
 *  — equivalent to .populate('sender', ...).populate('receiver', ...) in Mongoose. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDto {
    private String id;
    private UserSummary sender;
    private UserSummary receiver;
    private String toEmail;
    private String subject;
    private String body;
    private String htmlBody;
    private boolean isRead;
    private boolean isStarred;
    private boolean isImportant;
    private boolean isDraft;
    private boolean isArchived;
    private boolean isSent;
    private boolean trashedBySender;
    private boolean trashedByReceiver;
    private Instant trashedAt;
    private boolean isRecalled;
    private Instant recalledAt;
    private Instant scheduledTime;
    private String meetingLink;
    private List<String> cc;
    private List<String> bcc;
    private String folder;
    private List<Email.Mention> mentions;
    private List<Email.Attachment> attachments;
    private Instant deliveredAt;
    private Instant openedAt;
    private boolean smtpSent;
    private String smtpError;
    private Instant createdAt;
    private Instant updatedAt;
}
