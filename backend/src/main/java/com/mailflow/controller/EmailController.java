package com.mailflow.controller;

import com.mailflow.dto.EmailDto;
import com.mailflow.dto.PagedEmails;
import com.mailflow.exception.ApiException;
import com.mailflow.model.Email;
import com.mailflow.security.UserPrincipal;
import com.mailflow.service.EmailService;
import com.mailflow.service.FileStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Migrated from server/routes/email.js. Every endpoint path/method matches the original. */
@RestController
@RequestMapping("/api/email")
public class EmailController {

    private final EmailService emailService;
    private final FileStorageService fileStorageService;

    public EmailController(EmailService emailService, FileStorageService fileStorageService) {
        this.emailService = emailService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(value = "/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> send(@AuthenticationPrincipal UserPrincipal me,
                                     @RequestParam String to, @RequestParam String subject,
                                     @RequestParam(required = false) String body,
                                     @RequestParam(required = false) String htmlBody,
                                     @RequestParam(required = false) String meetingLink,
                                     @RequestParam(required = false) String cc,
                                     @RequestParam(required = false) String bcc,
                                     @RequestParam(required = false, defaultValue = "false") boolean isImportant,
                                     @RequestParam(required = false) String mentions,
                                     @RequestParam(value = "attachments", required = false) List<MultipartFile> attachments) {
        EmailDto dto = emailService.send(me.getUser(), to, subject, body, htmlBody, meetingLink, cc, bcc, isImportant, mentions, attachments);
        return Map.of("message", "Email sent successfully", "email", dto, "smtpSent", dto.isSmtpSent());
    }

    @PostMapping(value = "/draft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> saveDraft(@AuthenticationPrincipal UserPrincipal me,
                                          @RequestParam(required = false) String draftId,
                                          @RequestParam(required = false) String to,
                                          @RequestParam(required = false) String subject,
                                          @RequestParam(required = false) String body,
                                          @RequestParam(required = false) String htmlBody,
                                          @RequestParam(required = false) String meetingLink,
                                          @RequestParam(required = false) String cc,
                                          @RequestParam(required = false) String bcc,
                                          @RequestParam(required = false, defaultValue = "false") boolean isImportant,
                                          @RequestParam(required = false) String mentions,
                                          @RequestParam(value = "attachments", required = false) List<MultipartFile> attachments) {
        EmailDto dto = emailService.saveDraft(me.getUser(), draftId, to, subject, body, htmlBody, meetingLink, cc, bcc, isImportant, mentions, attachments);
        return Map.of("message", (draftId != null && !draftId.isBlank()) ? "Draft updated" : "Draft saved", "email", dto);
    }

    @PostMapping(value = "/draft/{id}/send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> sendDraft(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id,
                                          @RequestParam(required = false) String to,
                                          @RequestParam(required = false) String subject,
                                          @RequestParam(required = false) String body,
                                          @RequestParam(required = false) String htmlBody,
                                          @RequestParam(value = "attachments", required = false) List<MultipartFile> attachments) {
        EmailDto dto = emailService.sendDraft(me.getUser(), id, to, subject, body, htmlBody, attachments);
        return Map.of("message", "Draft sent", "email", dto, "smtpSent", dto.isSmtpSent());
    }

    @PostMapping(value = "/schedule", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> schedule(@AuthenticationPrincipal UserPrincipal me,
                                         @RequestParam String to, @RequestParam String subject,
                                         @RequestParam(required = false) String body,
                                         @RequestParam(required = false) String htmlBody,
                                         @RequestParam String scheduledTime,
                                         @RequestParam(required = false) String meetingLink,
                                         @RequestParam(required = false) String cc,
                                         @RequestParam(required = false) String bcc,
                                         @RequestParam(required = false, defaultValue = "false") boolean isImportant,
                                         @RequestParam(required = false) String mentions,
                                         @RequestParam(value = "attachments", required = false) List<MultipartFile> attachments) {
        Instant sendAt;
        try {
            sendAt = Instant.parse(scheduledTime);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "scheduledTime must be a valid future time");
        }
        EmailDto dto = emailService.schedule(me.getUser(), to, subject, body, htmlBody, sendAt, meetingLink, cc, bcc, isImportant, mentions, attachments);
        return Map.of("message", "Scheduled for " + sendAt, "email", dto);
    }

    @GetMapping("/inbox")
    public PagedEmails inbox(@AuthenticationPrincipal UserPrincipal me,
                              @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer limit) {
        return emailService.inbox(me.getUser(), page, limit);
    }

    @GetMapping("/sent")
    public PagedEmails sent(@AuthenticationPrincipal UserPrincipal me,
                             @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer limit) {
        return emailService.sent(me.getUser(), page, limit);
    }

    @GetMapping("/drafts")
    public Map<String, Object> drafts(@AuthenticationPrincipal UserPrincipal me) {
        return Map.of("emails", emailService.drafts(me.getUser()));
    }

    @GetMapping("/starred")
    public Map<String, Object> starred(@AuthenticationPrincipal UserPrincipal me) {
        return Map.of("emails", emailService.starred(me.getUser()));
    }

    @GetMapping("/important")
    public Map<String, Object> important(@AuthenticationPrincipal UserPrincipal me) {
        return Map.of("emails", emailService.important(me.getUser()));
    }

    @GetMapping("/archive")
    public Map<String, Object> archive(@AuthenticationPrincipal UserPrincipal me) {
        return Map.of("emails", emailService.archive(me.getUser()));
    }

    @GetMapping("/scheduled")
    public Map<String, Object> scheduled(@AuthenticationPrincipal UserPrincipal me) {
        return Map.of("emails", emailService.scheduled(me.getUser()));
    }

    @GetMapping("/trash")
    public Map<String, Object> trash(@AuthenticationPrincipal UserPrincipal me) {
        return Map.of("emails", emailService.trash(me.getUser()));
    }

    @GetMapping("/search")
    public Map<String, Object> search(@AuthenticationPrincipal UserPrincipal me, @RequestParam(required = false) String q) {
        return Map.of("emails", emailService.search(me.getUser(), q));
    }

    @GetMapping("/{id}")
    public Map<String, Object> getById(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id) {
        return Map.of("email", emailService.getById(me.getUser(), id));
    }

    @PatchMapping("/{id}/read")
    public Map<String, Object> toggleRead(@PathVariable String id) {
        return Map.of("isRead", emailService.toggleRead(id));
    }

    @PatchMapping("/{id}/star")
    public Map<String, Object> toggleStar(@PathVariable String id) {
        return Map.of("isStarred", emailService.toggleStar(id));
    }

    @PatchMapping("/{id}/important")
    public Map<String, Object> toggleImportant(@PathVariable String id) {
        return Map.of("isImportant", emailService.toggleImportant(id));
    }

    @PutMapping("/archive/{id}")
    public Map<String, Object> toggleArchive(@PathVariable String id) {
        return Map.of("isArchived", emailService.toggleArchive(id));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> moveToTrash(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id) {
        emailService.moveToTrash(me.getUser(), id);
        return Map.of("message", "Email moved to Trash");
    }

    @PatchMapping("/restore/{id}")
    public Map<String, Object> restore(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id) {
        emailService.restore(me.getUser(), id);
        return Map.of("message", "Email restored");
    }

    @DeleteMapping("/permanent/{id}")
    public Map<String, Object> permanentDelete(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id) {
        emailService.permanentDelete(me.getUser(), id);
        return Map.of("message", "Email permanently deleted");
    }

    @PatchMapping("/recall/{id}")
    public Map<String, Object> recall(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id) {
        EmailDto dto = emailService.recall(me.getUser(), id);
        return Map.of("message", "Email recalled successfully", "email", dto);
    }

    @GetMapping("/{id}/attachments/{attId}")
    public ResponseEntity<Resource> downloadAttachment(@AuthenticationPrincipal UserPrincipal me,
                                                         @PathVariable String id, @PathVariable String attId) {
        Email.Attachment att = emailService.getAttachmentForDownload(me.getUser(), id, attId);
        File file = new File(att.getPath());
        if (!file.exists()) file = fileStorageService.uploadsPath().resolve(att.getFilename()).toFile();
        if (!file.exists()) throw new ApiException(HttpStatus.NOT_FOUND, "File not found on server");

        Resource resource = new FileSystemResource(file);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(att.getOriginalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(att.getMimetype() != null ? MediaType.parseMediaType(att.getMimetype()) : MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.length())
                .body(resource);
    }
}
