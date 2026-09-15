package com.mailflow.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * Replaces server/config/nodemailer.js (createTransporter/verifySmtp) and
 * server/utils/sendMail.js. Spring Boot Mail auto-configures the
 * JavaMailSender bean from spring.mail.* properties (host/port/user/pass),
 * which map 1:1 to SMTP_HOST/SMTP_PORT/SMTP_USER/SMTP_PASS in the original
 * .env — see application.properties.
 *
 * Real emails are sent through Gmail SMTP exactly as before; nothing here
 * mocks or fakes delivery.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String smtpUser;

    @Value("${mailflow.smtp.from-name:Common Net}")
    private String fromName;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public record Attachment(String filename, File file) {}

    /**
     * Direct port of sendMail({ to, subject, text, html, attachments }).
     * Throws on failure exactly like the original (caller decides how to
     * record smtpSent/smtpError), and silently no-ops with a warning if SMTP
     * isn't configured — same "skipped" behavior as the Node version.
     */
    public void send(String to, String subject, String text, String html, List<Attachment> attachments) throws Exception {
        if (smtpUser == null || smtpUser.isBlank()) {
            log.warn("SMTP not configured — skipping real email send");
            return;
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, attachments != null && !attachments.isEmpty(), "UTF-8");
        helper.setFrom(smtpUser, fromName);
        helper.setTo(to);
        helper.setSubject(subject);
        String body = (html != null && !html.isBlank()) ? html : (text != null ? text : subject);
        helper.setText(text != null ? text : subject, body);

        if (attachments != null) {
            for (Attachment att : attachments) {
                helper.addAttachment(att.filename(), att.file());
            }
        }

        try {
            mailSender.send(message);
            log.info("Email sent -> {}", to);
        } catch (Exception e) {
            log.error("SMTP send failed -> {}: {}", to, e.getMessage());
            throw e;
        }
    }

    public void send(String to, String subject, String html) throws Exception {
        send(to, subject, null, html, null);
    }

    /** Non-blocking variant for "fire and forget" system emails (welcome, password-changed),
     *  matching the .catch(err => console.warn(...)) pattern used for those in the Node routes. */
    @Async
    public void sendAsyncBestEffort(String to, String subject, String html) {
        try {
            send(to, subject, html);
        } catch (Exception e) {
            log.warn("Best-effort email to {} failed: {}", to, e.getMessage());
        }
    }
}
