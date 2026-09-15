package com.mailflow.controller;

import com.mailflow.dto.AddAccountRequest;
import com.mailflow.dto.LogoutSessionRequest;
import com.mailflow.security.UserPrincipal;
import com.mailflow.service.AccountsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Migrated from server/routes/accounts.js. */
@RestController
@RequestMapping("/api/accounts")
public class AccountsController {

    private final AccountsService accountsService;

    public AccountsController(AccountsService accountsService) {
        this.accountsService = accountsService;
    }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal UserPrincipal me) {
        return Map.of("sessions", accountsService.listSessions(me.getUser()));
    }

    @PostMapping("/add")
    public Map<String, Object> add(@AuthenticationPrincipal UserPrincipal me, @RequestBody AddAccountRequest req) {
        return accountsService.addAccount(me.getUser(), req.getEmail(), req.getPassword(), req.getLabel());
    }

    @DeleteMapping("/logout-session")
    public Map<String, Object> logoutSession(@AuthenticationPrincipal UserPrincipal me, @RequestBody LogoutSessionRequest req) {
        accountsService.logoutSession(me.getUser(), req.getToken());
        return Map.of("message", "Session removed");
    }
}
