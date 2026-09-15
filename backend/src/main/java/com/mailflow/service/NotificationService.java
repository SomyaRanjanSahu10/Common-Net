package com.mailflow.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Replaces this pattern, repeated throughout routes/email.js and the cron
 * jobs in server.js:
 *
 *   function notify(req, userId, event, payload) {
 *     const io  = req.app.get('io');
 *     const sid = (req.app.get('userSockets') || {})[userId?.toString()];
 *     if (io && sid) io.to(sid).emit(event, payload);
 *   }
 *
 * Events preserved 1:1: "new_email", "important_email", "mention".
 * Frontend subscribes to /user/queue/notifications and switches on payload.type.
 */
@Service
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notify(String userId, String event, Object payload) {
        if (userId == null) return;
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("type", event);
        envelope.put("data", payload);
        messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", envelope);
    }

    public void newEmail(String userId, Object emailDto) {
        notify(userId, "new_email", emailDto);
    }

    public void importantEmail(String userId, Object emailDto) {
        notify(userId, "important_email", emailDto);
    }

    public void mention(String userId, Object emailDto, String mentionedByName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", emailDto);
        payload.put("mentionedBy", mentionedByName);
        notify(userId, "mention", payload);
    }
}
