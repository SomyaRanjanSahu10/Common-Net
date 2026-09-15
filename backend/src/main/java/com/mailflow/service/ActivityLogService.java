package com.mailflow.service;

import com.mailflow.model.ActivityLog;
import com.mailflow.repository.ActivityLogRepository;
import org.springframework.stereotype.Service;

/**
 * Replaces the logActivity() helper from server/middleware/security.js:
 *   const logActivity = async (userId, action, detail, ip, success) => {
 *     try { await ActivityLog.create({...}); } catch {}
 *   };
 * Swallows failures the same way (best-effort logging must never break a request).
 */
@Service
public class ActivityLogService {

    private final ActivityLogRepository repository;

    public ActivityLogService(ActivityLogRepository repository) {
        this.repository = repository;
    }

    public void log(String userId, String action, String detail, boolean success) {
        log(userId, action, detail, "", success);
    }

    public void log(String userId, String action, String detail, String ip, boolean success) {
        try {
            repository.save(ActivityLog.builder()
                    .user(userId)
                    .action(action)
                    .detail(detail == null ? "" : detail)
                    .ip(ip == null ? "" : ip)
                    .success(success)
                    .build());
        } catch (Exception ignored) {
            // best-effort — never fail the caller's request because logging failed
        }
    }
}
