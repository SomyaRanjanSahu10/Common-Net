package com.mailflow.service;

import com.mailflow.dto.EmailDto;
import com.mailflow.dto.PagedEmails;
import com.mailflow.exception.ApiException;
import com.mailflow.model.Email;
import com.mailflow.model.User;
import com.mailflow.repository.EmailRepository;
import com.mailflow.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Direct port of server/routes/email.js. Each public method here corresponds
 * 1:1 to a route handler; EmailController just adapts HTTP request/response.
 */
@Service
public class EmailService {

    private final EmailRepository emailRepository;
    private final UserRepository userRepository;
    private final EmailMapper emailMapper;
    private final FileStorageService fileStorageService;
    private final MailService mailService;
    private final NotificationService notificationService;

    public EmailService(EmailRepository emailRepository, UserRepository userRepository, EmailMapper emailMapper,
                         FileStorageService fileStorageService, MailService mailService,
                         NotificationService notificationService) {
        this.emailRepository = emailRepository;
        this.userRepository = userRepository;
        this.emailMapper = emailMapper;
        this.fileStorageService = fileStorageService;
        this.mailService = mailService;
        this.notificationService = notificationService;
    }

    private static List<String> splitList(String csv) {
        if (csv == null || csv.isBlank()) return new ArrayList<>();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    private List<Email.Mention> parseMentions(String raw) {
        List<Email.Mention> out = new ArrayList<>();
        for (String email : splitList(raw)) {
            userRepository.findByEmail(email.toLowerCase()).ifPresent(u ->
                    out.add(Email.Mention.builder().user(u.getId()).email(u.getEmail()).name(u.getName()).build()));
        }
        return out;
    }

    private List<Email.Attachment> mapFiles(List<MultipartFile> files) {
        if (files == null) return new ArrayList<>();
        return files.stream().filter(f -> f != null && !f.isEmpty()).map(f -> {
            FileStorageService.StoredFile stored = fileStorageService.storeAttachment(f);
            return Email.Attachment.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .originalName(stored.originalName())
                    .filename(stored.filename())
                    .mimetype(stored.mimetype())
                    .size(stored.size())
                    .path(stored.path())
                    .build();
        }).collect(Collectors.toList());
    }

    // ── POST /api/email/send ─────────────────────────────────────────────
    public EmailDto send(User me, String to, String subject, String body, String htmlBody, String meetingLink,
                          String cc, String bcc, boolean isImportant, String mentions, List<MultipartFile> files) {
        if (to == null || to.isBlank() || subject == null || subject.isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Recipient and subject are required");

        User receiver = userRepository.findByEmail(to.toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No user found with email: " + to));
        if (receiver.getId().equals(me.getId()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot send an email to yourself");

        List<Email.Mention> mentionList = parseMentions(mentions);
        List<Email.Attachment> attachments = mapFiles(files);

        Email email = Email.builder()
                .sender(me.getId()).receiver(receiver.getId()).toEmail(to.toLowerCase())
                .subject(subject).body(body == null ? "" : body).htmlBody(htmlBody == null ? "" : htmlBody)
                .meetingLink(meetingLink == null ? "" : meetingLink)
                .cc(splitList(cc)).bcc(splitList(bcc))
                .isImportant(isImportant)
                .mentions(mentionList).attachments(attachments)
                .isSent(true).deliveredAt(Instant.now())
                .build();
        email = emailRepository.save(email);

        EmailDto dto = emailMapper.toDto(email);
        notificationService.newEmail(receiver.getId(), dto);
        if (email.isImportant()) notificationService.importantEmail(receiver.getId(), dto);
        for (Email.Mention m : mentionList) {
            if (!m.getUser().equals(receiver.getId())) notificationService.mention(m.getUser(), dto, me.getName());
        }

        boolean smtpSent = false;
        String smtpError = null;
        try {
            // Reuse the already-persisted attachment files (mapFiles() above) rather than
            // re-reading the MultipartFiles a second time — MultipartFile.transferTo() is
            // not guaranteed to be safely callable twice on every servlet container.
            List<MailService.Attachment> mailAtts = attachments.stream()
                    .map(a -> new MailService.Attachment(a.getOriginalName(), new java.io.File(a.getPath())))
                    .collect(Collectors.toList());
            mailService.send(receiver.getEmail(), subject, body, (htmlBody != null && !htmlBody.isBlank()) ? htmlBody : body, mailAtts);
            smtpSent = true;
        } catch (Exception e) {
            smtpError = e.getMessage();
        }
        email.setSmtpSent(smtpSent);
        email.setSmtpError(smtpError);
        emailRepository.save(email);

        dto.setSmtpSent(smtpSent);
        return dto;
    }

    // ── POST /api/email/draft ────────────────────────────────────────────
    public EmailDto saveDraft(User me, String draftId, String to, String subject, String body, String htmlBody,
                               String meetingLink, String cc, String bcc, boolean isImportant, String mentions,
                               List<MultipartFile> files) {
        User receiver = (to != null && !to.isBlank()) ? userRepository.findByEmail(to.toLowerCase()).orElse(null) : null;
        List<Email.Attachment> attachments = mapFiles(files);
        List<Email.Mention> mentionList = parseMentions(mentions);

        if (draftId != null && !draftId.isBlank()) {
            Email draft = emailRepository.findById(draftId)
                    .filter(e -> e.getSender().equals(me.getId()) && e.isDraft())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Draft not found"));

            draft.setReceiver(receiver != null ? receiver.getId() : null);
            draft.setToEmail(to != null ? to.toLowerCase() : "");
            if (subject != null) draft.setSubject(subject);
            if (body != null) draft.setBody(body);
            if (htmlBody != null) draft.setHtmlBody(htmlBody);
            draft.setMeetingLink(meetingLink == null ? "" : meetingLink);
            draft.setCc(splitList(cc));
            draft.setBcc(splitList(bcc));
            draft.setImportant(isImportant);
            draft.setMentions(mentionList);
            if (!attachments.isEmpty()) draft.getAttachments().addAll(attachments);
            draft = emailRepository.save(draft);
            return emailMapper.toDto(draft);
        }

        Email email = Email.builder()
                .sender(me.getId()).receiver(receiver != null ? receiver.getId() : null)
                .toEmail(to != null ? to.toLowerCase() : "")
                .subject((subject == null || subject.isBlank()) ? "(No Subject)" : subject)
                .body(body == null ? "" : body).htmlBody(htmlBody == null ? "" : htmlBody)
                .meetingLink(meetingLink == null ? "" : meetingLink)
                .cc(splitList(cc)).bcc(splitList(bcc))
                .isImportant(isImportant)
                .mentions(mentionList).attachments(attachments)
                .isDraft(true)
                .build();
        email = emailRepository.save(email);
        return emailMapper.toDto(email);
    }

    // ── POST /api/email/draft/:id/send ───────────────────────────────────
    public EmailDto sendDraft(User me, String draftId, String to, String subject, String body, String htmlBody,
                               List<MultipartFile> files) {
        Email draft = emailRepository.findById(draftId)
                .filter(e -> e.getSender().equals(me.getId()) && e.isDraft())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Draft not found"));

        String toEmail = (to != null && !to.isBlank()) ? to.toLowerCase() : draft.getToEmail();
        if (toEmail == null || toEmail.isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Recipient required");
        User receiver = userRepository.findByEmail(toEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No user: " + toEmail));

        draft.setReceiver(receiver.getId());
        draft.setToEmail(toEmail);
        if (subject != null) draft.setSubject(subject);
        if (body != null) draft.setBody(body);
        if (htmlBody != null) draft.setHtmlBody(htmlBody);
        draft.setDraft(false);
        draft.setSent(true);
        draft.setDeliveredAt(Instant.now());
        List<Email.Attachment> newAtts = mapFiles(files);
        if (!newAtts.isEmpty()) draft.getAttachments().addAll(newAtts);
        draft = emailRepository.save(draft);

        EmailDto dto = emailMapper.toDto(draft);
        notificationService.newEmail(receiver.getId(), dto);

        boolean smtpSent = false;
        try {
            mailService.send(receiver.getEmail(), draft.getSubject(), draft.getBody(),
                    (draft.getHtmlBody() != null && !draft.getHtmlBody().isBlank()) ? draft.getHtmlBody() : draft.getBody(), null);
            smtpSent = true;
        } catch (Exception ignored) {
        }
        draft.setSmtpSent(smtpSent);
        emailRepository.save(draft);
        dto.setSmtpSent(smtpSent);
        return dto;
    }

    // ── POST /api/email/schedule ──────────────────────────────────────────
    public EmailDto schedule(User me, String to, String subject, String body, String htmlBody, Instant scheduledTime,
                              String meetingLink, String cc, String bcc, boolean isImportant, String mentions,
                              List<MultipartFile> files) {
        if (to == null || to.isBlank() || subject == null || subject.isBlank() || scheduledTime == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "to, subject, scheduledTime required");
        if (!scheduledTime.isAfter(Instant.now()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "scheduledTime must be a valid future time");

        User receiver = userRepository.findByEmail(to.toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No user: " + to));

        Email email = Email.builder()
                .sender(me.getId()).receiver(receiver.getId()).toEmail(to.toLowerCase())
                .subject(subject).body(body == null ? "" : body).htmlBody(htmlBody == null ? "" : htmlBody)
                .meetingLink(meetingLink == null ? "" : meetingLink)
                .cc(splitList(cc)).bcc(splitList(bcc))
                .isImportant(isImportant)
                .mentions(parseMentions(mentions)).attachments(mapFiles(files))
                .scheduledTime(scheduledTime).isSent(false).isDraft(false)
                .build();
        email = emailRepository.save(email);
        return emailMapper.toDto(email);
    }

    // ── Listing endpoints ────────────────────────────────────────────────
    private PageRequest pageRequest(Integer page, Integer limit, Sort sort) {
        int p = (page == null || page < 1) ? 1 : page;
        int l = (limit == null) ? 20 : Math.min(50, limit);
        return PageRequest.of(p - 1, l, sort);
    }

    public PagedEmails inbox(User me, Integer page, Integer limit) {
        PageRequest pr = pageRequest(page, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Email> result = emailRepository.findInbox(me.getId(), pr);
        long unread = result.getContent().stream().filter(e -> !e.isRead()).count();
        return PagedEmails.builder()
                .emails(emailMapper.toDtoList(result.getContent()))
                .total(result.getTotalElements())
                .page(pr.getPageNumber() + 1)
                .pages(result.getTotalPages())
                .unreadCount(unread)
                .build();
    }

    public PagedEmails sent(User me, Integer page, Integer limit) {
        PageRequest pr = pageRequest(page, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Email> result = emailRepository.findSent(me.getId(), pr);
        return PagedEmails.builder().emails(emailMapper.toDtoList(result.getContent()))
                .total(result.getTotalElements()).page(pr.getPageNumber() + 1).pages(result.getTotalPages()).build();
    }

    public List<EmailDto> drafts(User me) {
        Page<Email> result = emailRepository.findDrafts(me.getId(), PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "updatedAt")));
        return emailMapper.toDtoList(result.getContent());
    }

    public List<EmailDto> starred(User me) {
        Page<Email> result = emailRepository.findStarred(me.getId(), PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "createdAt")));
        return emailMapper.toDtoList(result.getContent());
    }

    public List<EmailDto> important(User me) {
        Page<Email> result = emailRepository.findImportant(me.getId(), PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "createdAt")));
        return emailMapper.toDtoList(result.getContent());
    }

    public List<EmailDto> archive(User me) {
        Page<Email> result = emailRepository.findArchived(me.getId(), PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "createdAt")));
        return emailMapper.toDtoList(result.getContent());
    }

    public List<EmailDto> scheduled(User me) {
        Page<Email> result = emailRepository.findScheduled(me.getId(), PageRequest.of(0, 1000, Sort.by(Sort.Direction.ASC, "scheduledTime")));
        return emailMapper.toDtoList(result.getContent());
    }

    public List<EmailDto> trash(User me) {
        Page<Email> result = emailRepository.findTrash(me.getId(), PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "trashedAt")));
        return emailMapper.toDtoList(result.getContent());
    }

    public List<EmailDto> search(User me, String q) {
        Page<Email> result = emailRepository.search(me.getId(), q == null ? "" : q, PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt")));
        return emailMapper.toDtoList(result.getContent());
    }

    // ── GET /api/email/:id ──────────────────────────────────────────────
    public EmailDto getById(User me, String id) {
        Email email = emailRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Email not found"));
        if (me.getId().equals(email.getReceiver()) && !email.isRead()) {
            email.setRead(true);
            email.setOpenedAt(Instant.now());
            emailRepository.save(email);
        }
        return emailMapper.toDto(email);
    }

    private Email findOwned(String id) {
        return emailRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Email not found"));
    }

    public boolean toggleRead(String id) {
        Email email = findOwned(id);
        email.setRead(!email.isRead());
        if (email.isRead() && email.getOpenedAt() == null) email.setOpenedAt(Instant.now());
        emailRepository.save(email);
        return email.isRead();
    }

    public boolean toggleStar(String id) {
        Email email = findOwned(id);
        email.setStarred(!email.isStarred());
        emailRepository.save(email);
        return email.isStarred();
    }

    public boolean toggleImportant(String id) {
        Email email = findOwned(id);
        email.setImportant(!email.isImportant());
        emailRepository.save(email);
        return email.isImportant();
    }

    public boolean toggleArchive(String id) {
        Email email = findOwned(id);
        email.setArchived(!email.isArchived());
        emailRepository.save(email);
        return email.isArchived();
    }

    // ── DELETE /api/email/:id → move to Trash ────────────────────────────
    public void moveToTrash(User me, String id) {
        Email email = findOwned(id);
        boolean changed = false;
        if (me.getId().equals(email.getSender()) && !email.isTrashedBySender()) { email.setTrashedBySender(true); changed = true; }
        if (me.getId().equals(email.getReceiver()) && !email.isTrashedByReceiver()) { email.setTrashedByReceiver(true); changed = true; }
        if (changed && email.getTrashedAt() == null) email.setTrashedAt(Instant.now());
        emailRepository.save(email);
    }

    public void restore(User me, String id) {
        Email email = findOwned(id);
        if (me.getId().equals(email.getSender())) email.setTrashedBySender(false);
        if (me.getId().equals(email.getReceiver())) email.setTrashedByReceiver(false);
        if (!email.isTrashedBySender() && !email.isTrashedByReceiver()) email.setTrashedAt(null);
        emailRepository.save(email);
    }

    public void permanentDelete(User me, String id) {
        Email email = findOwned(id);
        if (me.getId().equals(email.getSender())) email.setDeletedBySender(true);
        if (me.getId().equals(email.getReceiver())) email.setDeletedByReceiver(true);
        if (email.isDeletedBySender() && (email.isDeletedByReceiver() || email.getReceiver() == null)) {
            emailRepository.delete(email);
        } else {
            emailRepository.save(email);
        }
    }

    // ── PATCH /api/email/recall/:id ──────────────────────────────────────
    public EmailDto recall(User me, String id) {
        Email email = findOwned(id);
        if (!me.getId().equals(email.getSender()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Only sender can recall");
        if (!email.isSent() || email.isDraft())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only sent emails can be recalled");
        if (email.isRecalled())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Already recalled");

        Instant sentAt = email.getDeliveredAt() != null ? email.getDeliveredAt() : email.getCreatedAt();
        double elapsedMinutes = (Instant.now().toEpochMilli() - sentAt.toEpochMilli()) / 1000.0 / 60.0;
        if (elapsedMinutes > 2)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Recall window expired (2 minutes)");

        email.setRecalled(true);
        email.setRecalledAt(Instant.now());
        emailRepository.save(email);

        EmailDto dto = emailMapper.toDto(email);
        if (email.getReceiver() != null) {
            notificationService.notify(email.getReceiver(), "email_recalled", java.util.Map.of("emailId", email.getId()));
        }
        return dto;
    }

    // ── GET /api/email/:id/attachments/:attId ────────────────────────────
    public Email.Attachment getAttachmentForDownload(User me, String id, String attId) {
        Email email = findOwned(id);
        if (!me.getId().equals(email.getSender()) && !me.getId().equals(email.getReceiver()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
        return email.getAttachments().stream().filter(a -> a.getId().equals(attId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Attachment not found"));
    }
}
