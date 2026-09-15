package com.mailflow.controller;

import com.mailflow.dto.AdminResetPasswordRequest;
import com.mailflow.model.User;
import com.mailflow.security.UserPrincipal;
import com.mailflow.service.AdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Migrated from server/routes/admin.js. Access control is enforced both at
 * the URL level in SecurityConfig (/api/admin/** -> hasRole("ADMIN")) and
 * again here with @PreAuthorize as defense in depth, replacing the Node
 * `router.use(protect, adminOnly)`.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return adminService.dashboard();
    }

    @GetMapping("/users")
    public Map<String, Object> listUsers(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int limit,
                                          @RequestParam(required = false) String search) {
        return adminService.listUsers(page, limit, search);
    }

    @PatchMapping("/users/{id}/toggle-active")
    public Map<String, Object> toggleActive(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id) {
        boolean isActive = adminService.toggleActive(me.getUser(), id);
        return Map.of("message", "User " + (isActive ? "activated" : "suspended"), "isActive", isActive);
    }

    @PatchMapping("/users/{id}/make-admin")
    public Map<String, Object> makeAdmin(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id) {
        User user = adminService.makeAdmin(me.getUser(), id);
        return Map.of("message", "Promoted to admin", "user", user);
    }

    @PatchMapping("/users/{id}/reset-password")
    public Map<String, Object> resetPassword(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id,
                                              @RequestBody AdminResetPasswordRequest req) {
        adminService.resetPassword(me.getUser(), id, req.getNewPassword());
        return Map.of("message", "Password reset successfully");
    }

    @DeleteMapping("/users/{id}")
    public Map<String, Object> deleteUser(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id) {
        adminService.deleteUser(me.getUser(), id);
        return Map.of("message", "User deleted");
    }

    @GetMapping("/emails")
    public Map<String, Object> listEmails(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int limit) {
        return adminService.listEmails(page, limit);
    }

    @DeleteMapping("/emails/{id}")
    public Map<String, Object> deleteEmail(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id) {
        adminService.deleteEmail(me.getUser(), id);
        return Map.of("message", "Email deleted by admin");
    }

    @GetMapping("/logs")
    public Map<String, Object> logs(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "30") int limit) {
        return adminService.logs(page, limit);
    }

    @GetMapping("/activity")
    public Map<String, Object> activity(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "30") int limit) {
        return adminService.activity(page, limit);
    }
}
