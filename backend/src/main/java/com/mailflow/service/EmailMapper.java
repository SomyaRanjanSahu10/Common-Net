package com.mailflow.service;

import com.mailflow.dto.EmailDto;
import com.mailflow.dto.UserSummary;
import com.mailflow.model.Email;
import com.mailflow.model.User;
import com.mailflow.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Replaces the repeated `.populate('sender', 'name email avatar designation')
 * .populate('receiver', 'name email avatar designation')` calls throughout
 * routes/email.js. Batches user lookups so a page of emails only costs one
 * extra query instead of N.
 */
@Component
public class EmailMapper {

    private final UserRepository userRepository;

    public EmailMapper(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public EmailDto toDto(Email e) {
        User sender = e.getSender() != null ? userRepository.findById(e.getSender()).orElse(null) : null;
        User receiver = e.getReceiver() != null ? userRepository.findById(e.getReceiver()).orElse(null) : null;
        return build(e, sender, receiver);
    }

    public List<EmailDto> toDtoList(List<Email> emails) {
        Set<String> userIds = emails.stream()
                .flatMap(e -> java.util.stream.Stream.of(e.getSender(), e.getReceiver()))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a, HashMap::new));

        return emails.stream()
                .map(e -> build(e, users.get(e.getSender()), users.get(e.getReceiver())))
                .collect(Collectors.toList());
    }

    private EmailDto build(Email e, User sender, User receiver) {
        return EmailDto.builder()
                .id(e.getId())
                .sender(UserSummary.from(sender))
                .receiver(UserSummary.from(receiver))
                .toEmail(e.getToEmail())
                .subject(e.getSubject())
                .body(e.getBody())
                .htmlBody(e.getHtmlBody())
                .isRead(e.isRead())
                .isStarred(e.isStarred())
                .isImportant(e.isImportant())
                .isDraft(e.isDraft())
                .isArchived(e.isArchived())
                .isSent(e.isSent())
                .trashedBySender(e.isTrashedBySender())
                .trashedByReceiver(e.isTrashedByReceiver())
                .trashedAt(e.getTrashedAt())
                .isRecalled(e.isRecalled())
                .recalledAt(e.getRecalledAt())
                .scheduledTime(e.getScheduledTime())
                .meetingLink(e.getMeetingLink())
                .cc(e.getCc())
                .bcc(e.getBcc())
                .folder(e.getFolder())
                .mentions(e.getMentions())
                .attachments(e.getAttachments())
                .deliveredAt(e.getDeliveredAt())
                .openedAt(e.getOpenedAt())
                .smtpSent(e.isSmtpSent())
                .smtpError(e.getSmtpError())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
