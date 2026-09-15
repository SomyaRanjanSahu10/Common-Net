package com.mailflow.controller;

import com.mailflow.dto.SignatureRequest;
import com.mailflow.model.Signature;
import com.mailflow.security.UserPrincipal;
import com.mailflow.service.SignatureService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/signature")
public class SignatureController {

    private final SignatureService signatureService;

    public SignatureController(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @GetMapping
    public Map<String, Object> get(@AuthenticationPrincipal UserPrincipal me) {
        Map<String, Object> body = new HashMap<>();
        body.put("signature", signatureService.get(me.getUser()).orElse(null));
        return body;
    }

    @PostMapping
    public Map<String, Object> save(@AuthenticationPrincipal UserPrincipal me, @RequestBody SignatureRequest req) {
        Signature sig = signatureService.save(me.getUser(), req.getName(), req.getDesignation(), req.getCompany(),
                req.getPhone(), req.getRegards(), req.getHtmlContent(), req.getIsEnabled());
        return Map.of("message", "Signature saved", "signature", sig);
    }

    @DeleteMapping
    public Map<String, Object> delete(@AuthenticationPrincipal UserPrincipal me) {
        signatureService.delete(me.getUser());
        return Map.of("message", "Signature removed");
    }

    /**
     * Used by js/compose.js on Compose open to fetch ready-to-insert
     * signature HTML — the fix for the disabled auto-insertion feature.
     * Returns "" if the user has no signature or has disabled it, so the
     * frontend can simply skip insertion.
     */
    @GetMapping("/render")
    public Map<String, Object> render(@AuthenticationPrincipal UserPrincipal me) {
        Signature sig = signatureService.get(me.getUser()).orElse(null);
        return Map.of("html", signatureService.renderHtml(sig));
    }
}
