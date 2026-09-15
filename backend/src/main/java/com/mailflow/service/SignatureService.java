package com.mailflow.service;

import com.mailflow.model.Signature;
import com.mailflow.model.User;
import com.mailflow.repository.SignatureRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Direct port of server/routes/signature.js (Feature 2). */
@Service
public class SignatureService {

    private final SignatureRepository signatureRepository;

    public SignatureService(SignatureRepository signatureRepository) {
        this.signatureRepository = signatureRepository;
    }

    public Optional<Signature> get(User user) {
        return signatureRepository.findByUser(user.getId());
    }

    public Signature save(User user, String name, String designation, String company, String phone,
                           String regards, String htmlContent, Boolean isEnabled) {
        Signature sig = signatureRepository.findByUser(user.getId())
                .orElse(Signature.builder().user(user.getId()).build());
        sig.setName(name == null ? "" : name);
        sig.setDesignation(designation == null ? "" : designation);
        sig.setCompany(company == null ? "" : company);
        sig.setPhone(phone == null ? "" : phone);
        sig.setRegards(regards == null ? "Best Regards" : regards);
        sig.setHtmlContent(htmlContent == null ? "" : htmlContent);
        sig.setEnabled(isEnabled == null || isEnabled);
        return signatureRepository.save(sig);
    }

    public void delete(User user) {
        signatureRepository.deleteByUser(user.getId());
    }

    /**
     * Renders the signature to HTML for insertion into a composed email.
     * Used by EmailController's compose-prefill endpoint — this is the fix
     * for the bug noted in the original React code
     * (`// Signature auto-insertion disabled.`), now wired end-to-end:
     * Signature Settings -> save -> Compose auto-fetches and inserts this ->
     * Send -> included in the outgoing email body.
     */
    public String renderHtml(Signature sig) {
        if (sig == null || !sig.isEnabled()) return "";
        if (sig.getHtmlContent() != null && !sig.getHtmlContent().isBlank()) {
            return sig.getHtmlContent();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"mf-signature\" style=\"margin-top:16px;padding-top:12px;border-top:1px solid #e1dfdd;color:#333;font-family:sans-serif;font-size:13px;\">");
        sb.append("<p style=\"margin:0 0 4px;\">").append(escape(sig.getRegards())).append(",</p>");
        if (sig.getName() != null && !sig.getName().isBlank())
            sb.append("<p style=\"margin:0;font-weight:600;\">").append(escape(sig.getName())).append("</p>");
        if (sig.getDesignation() != null && !sig.getDesignation().isBlank())
            sb.append("<p style=\"margin:0;\">").append(escape(sig.getDesignation())).append("</p>");
        if (sig.getCompany() != null && !sig.getCompany().isBlank())
            sb.append("<p style=\"margin:0;\">").append(escape(sig.getCompany())).append("</p>");
        if (sig.getPhone() != null && !sig.getPhone().isBlank())
            sb.append("<p style=\"margin:0;\">").append(escape(sig.getPhone())).append("</p>");
        sb.append("</div>");
        return sb.toString();
    }

    private String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
