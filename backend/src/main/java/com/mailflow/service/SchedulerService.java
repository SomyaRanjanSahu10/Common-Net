package com.mailflow.service;

import com.mailflow.dto.EmailDto;
import com.mailflow.model.Email;
import com.mailflow.repository.EmailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Direct port of the two cron.schedule(...) jobs in server.js:
 *
 *   1. '* * * * *'  — deliver due scheduled emails, every minute
 *   2. '0 2 * * *'  — purge trash older than 30 days, daily at 02:00
 *
 * @Scheduled(cron=...) uses the same 5/6-field cron syntax semantics as
 * node-cron (Spring's is 6-field: seconds first), so the expressions below
 * are the direct equivalents with a leading "0" for seconds.
 */
@Service
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final EmailRepository emailRepository;
    private final EmailMapper emailMapper;
    private final NotificationService notificationService;
    private final MailService mailService;

    public SchedulerService(EmailRepository emailRepository, EmailMapper emailMapper,
                             NotificationService notificationService, MailService mailService) {
        this.emailRepository = emailRepository;
        this.emailMapper = emailMapper;
        this.notificationService = notificationService;
        this.mailService = mailService;
    }

    /** CRON 1 — equivalent to cron.schedule('* * * * *', ...). */
    @Scheduled(cron = "0 * * * * *")
    public void deliverScheduledEmails() {
        try {
            List<Email> due = emailRepository
                    .findByIsDraftFalseAndIsSentFalseAndScheduledTimeLessThanEqualAndReceiverIsNotNull(Instant.now());

            for (Email email : due) {
                email.setSent(true);
                email.setDeliveredAt(Instant.now());
                emailRepository.save(email);

                EmailDto dto = emailMapper.toDto(email);
                notificationService.newEmail(email.getReceiver(), dto);
                if (email.isImportant()) notificationService.importantEmail(email.getReceiver(), dto);

                try {
                    mailService.send(dto.getReceiver() != null ? dto.getReceiver().getEmail() : email.getToEmail(),
                            email.getSubject(), email.getBody(),
                            (email.getHtmlBody() != null && !email.getHtmlBody().isBlank()) ? email.getHtmlBody() : email.getBody(),
                            null);
                    email.setSmtpSent(true);
                } catch (Exception e) {
                    email.setSmtpError(e.getMessage());
                } finally {
                    emailRepository.save(email);
                }

                log.info("Scheduled email {} delivered", email.getId());
            }
        } catch (Exception e) {
            log.error("Scheduled cron: {}", e.getMessage());
        }
    }

    /** CRON 2 — equivalent to cron.schedule('0 2 * * *', ...), daily at 02:00. */
    @Scheduled(cron = "0 0 2 * * *")
    public void purgeOldTrash() {
        try {
            Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
            List<Email> expired = emailRepository.findExpiredTrash(cutoff);
            if (!expired.isEmpty()) {
                emailRepository.deleteAll(expired);
                log.info("Auto-deleted {} trashed emails older than 30 days", expired.size());
            }
        } catch (Exception e) {
            log.error("Trash cron: {}", e.getMessage());
        }
    }
}
