package com.mailflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables @Async so SMTP sending (MailService) doesn't block the request thread,
 *  mirroring the non-blocking nature of nodemailer's promise-based sendMail(). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
