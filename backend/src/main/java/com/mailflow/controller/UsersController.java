package com.mailflow.controller;

import com.mailflow.model.User;
import com.mailflow.repository.UserRepository;
import com.mailflow.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Migrated from server/routes/users.js (compose-recipient search/autocomplete). */
@RestController
@RequestMapping("/api/users")
public class UsersController {

    private final UserRepository userRepository;

    public UsersController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record UserSlim(String id, String name, String email) {
        static UserSlim from(User u) { return new UserSlim(u.getId(), u.getName(), u.getEmail()); }
    }

    @GetMapping("/search")
    public Map<String, Object> search(@AuthenticationPrincipal UserPrincipal me, @RequestParam(required = false) String q) {
        if (q == null || q.length() < 2) return Map.of("users", List.of());
        String regex = Pattern.quote(q);
        List<UserSlim> users = userRepository.searchByNameOrEmail(regex).stream()
                .filter(u -> !u.getId().equals(me.getId()))
                .limit(5)
                .map(UserSlim::from)
                .collect(Collectors.toList());
        return Map.of("users", users);
    }

    @GetMapping
    public Map<String, Object> all(@AuthenticationPrincipal UserPrincipal me) {
        List<UserSlim> users = userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(me.getId()))
                .map(UserSlim::from)
                .collect(Collectors.toList());
        return Map.of("users", users);
    }
}
