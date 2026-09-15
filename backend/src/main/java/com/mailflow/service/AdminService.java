package com.mailflow.service;

import com.mailflow.dto.DashboardStats;
import com.mailflow.dto.EmailDto;
import com.mailflow.exception.ApiException;
import com.mailflow.model.AdminLog;
import com.mailflow.model.Email;
import com.mailflow.model.User;
import com.mailflow.repository.AdminLogRepository;
import com.mailflow.repository.EmailRepository;
import com.mailflow.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/** Direct port of server/routes/admin.js — every method here is guarded at the
 *  controller layer by Spring Security's hasRole("ADMIN") (see SecurityConfig),
 *  replacing the Node `router.use(protect, adminOnly)`. */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final EmailRepository emailRepository;
    private final AdminLogRepository adminLogRepository;
    private final com.mailflow.repository.ActivityLogRepository activityLogRepository;
    private final EmailMapper emailMapper;
    private final MongoTemplate mongoTemplate;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserRepository userRepository, EmailRepository emailRepository,
                         AdminLogRepository adminLogRepository,
                         com.mailflow.repository.ActivityLogRepository activityLogRepository,
                         EmailMapper emailMapper, MongoTemplate mongoTemplate, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.emailRepository = emailRepository;
        this.adminLogRepository = adminLogRepository;
        this.activityLogRepository = activityLogRepository;
        this.emailMapper = emailMapper;
        this.mongoTemplate = mongoTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    private void log(User admin, String action, String target, String detail) {
        try {
            adminLogRepository.save(AdminLog.builder()
                    .admin(admin.getId()).action(action).target(target == null ? "" : target)
                    .detail(detail == null ? "" : detail).build());
        } catch (Exception ignored) {
        }
    }

    public Map<String, Object> dashboard() {
        long totalUsers = userRepository.count();
        long totalEmails = emailRepository.countByIsDraftFalseAndIsSentTrue();
        long activeUsers = userRepository.countByIsActive(true);
        Instant startOfDay = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
        long sentToday = emailRepository.countByIsSentTrueAndCreatedAtGreaterThanEqual(startOfDay);
        long trashCount = emailRepository.countTrashed();
        long deletedCount = emailRepository.countDeleted();

        List<Email> recent = emailRepository.findTop10ByIsDraftFalseAndIsSentTrueOrderByCreatedAtDesc();
        List<EmailDto> recentEmails = emailMapper.toDtoList(recent);

        List<AdminLog> recentLogs = adminLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 20)).getContent();
        List<com.mailflow.model.ActivityLog> activityLogs =
                activityLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 20)).getContent();

        DashboardStats stats = DashboardStats.builder()
                .totalUsers(totalUsers).totalEmails(totalEmails).activeUsers(activeUsers)
                .sentToday(sentToday).trashCount(trashCount).deletedCount(deletedCount).build();

        return Map.of("stats", stats, "recentEmails", recentEmails, "recentLogs", recentLogs, "activityLogs", activityLogs);
    }

    public Map<String, Object> listUsers(int page, int limit, String search) {
        Query q = new Query();
        if (search != null && !search.isBlank()) {
            q.addCriteria(new org.springframework.data.mongodb.core.query.Criteria().orOperator(
                    where("name").regex(search, "i"), where("email").regex(search, "i")));
        }
        long total = mongoTemplate.count(q, User.class);
        q.with(Sort.by(Sort.Direction.DESC, "createdAt")).skip((long) (page - 1) * limit).limit(limit);
        List<User> users = mongoTemplate.find(q, User.class);
        int pages = (int) Math.ceil((double) total / limit);
        return Map.of("users", users, "total", total, "page", page, "pages", pages);
    }

    private User findUser(String id) {
        return userRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public boolean toggleActive(User admin, String userId) {
        User user = findUser(userId);
        if ("admin".equals(user.getRole())) throw new ApiException(HttpStatus.FORBIDDEN, "Cannot suspend admin");
        user.setActive(!user.isActive());
        userRepository.save(user);
        log(admin, user.isActive() ? "ACTIVATE_USER" : "SUSPEND_USER", user.getId(), user.getEmail());
        return user.isActive();
    }

    public User makeAdmin(User admin, String userId) {
        User user = findUser(userId);
        user.setRole("admin");
        user = userRepository.save(user);
        log(admin, "PROMOTE_ADMIN", user.getId(), user.getEmail());
        return user;
    }

    public void resetPassword(User admin, String userId, String newPassword) {
        if (newPassword == null || newPassword.length() < 6)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must be 6+ characters");
        User user = findUser(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log(admin, "RESET_PASSWORD", user.getId(), user.getEmail());
    }

    public void deleteUser(User admin, String userId) {
        User user = findUser(userId);
        if ("admin".equals(user.getRole())) throw new ApiException(HttpStatus.FORBIDDEN, "Cannot delete admin");
        log(admin, "DELETE_USER", user.getId(), user.getName() + " <" + user.getEmail() + ">");
        mongoTemplate.updateMulti(Query.query(where("sender").is(user.getId())), Update.update("deletedBySender", true), Email.class);
        mongoTemplate.updateMulti(Query.query(where("receiver").is(user.getId())), Update.update("deletedByReceiver", true), Email.class);
        userRepository.delete(user);
    }

    public Map<String, Object> listEmails(int page, int limit) {
        Query q = Query.query(where("isDraft").is(false))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) (page - 1) * limit).limit(limit);
        long total = mongoTemplate.count(Query.query(where("isDraft").is(false)), Email.class);
        List<Email> emails = mongoTemplate.find(q, Email.class);
        // Strip body/htmlBody, mirroring .select('-body -htmlBody')
        List<Map<String, Object>> slim = emailMapper.toDtoList(emails).stream().map(e -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", e.getId()); m.put("sender", e.getSender()); m.put("receiver", e.getReceiver());
            m.put("subject", e.getSubject()); m.put("createdAt", e.getCreatedAt());
            m.put("isSent", e.isSent()); m.put("smtpSent", e.isSmtpSent()); m.put("isRecalled", e.isRecalled());
            return m;
        }).collect(Collectors.toList());
        int pages = (int) Math.ceil((double) total / limit);
        return Map.of("emails", slim, "total", total, "page", page, "pages", pages);
    }

    public void deleteEmail(User admin, String emailId) {
        Email email = emailRepository.findById(emailId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Email not found"));
        log(admin, "DELETE_EMAIL", email.getId(), email.getSubject());
        emailRepository.delete(email);
    }

    public Map<String, Object> logs(int page, int limit) {
        Page<AdminLog> result = adminLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page - 1, limit));
        return Map.of("logs", result.getContent(), "total", result.getTotalElements(), "page", page, "pages", result.getTotalPages());
    }

    public Map<String, Object> activity(int page, int limit) {
        Page<com.mailflow.model.ActivityLog> result = activityLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page - 1, limit));
        return Map.of("logs", result.getContent(), "total", result.getTotalElements(), "page", page, "pages", result.getTotalPages());
    }
}
