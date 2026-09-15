package com.mailflow.controller;

import com.mailflow.dto.FolderRequest;
import com.mailflow.model.Folder;
import com.mailflow.security.UserPrincipal;
import com.mailflow.service.FolderService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal UserPrincipal me) {
        return Map.of("folders", folderService.list(me.getUser()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@AuthenticationPrincipal UserPrincipal me, @RequestBody FolderRequest req) {
        Folder folder = folderService.create(me.getUser(), req.getName(), req.getColor());
        return Map.of("folder", folder);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id, @RequestBody FolderRequest req) {
        Folder folder = folderService.update(me.getUser(), id, req.getName(), req.getColor());
        return Map.of("folder", folder);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id) {
        folderService.delete(me.getUser(), id);
        return Map.of("message", "Folder deleted");
    }

    @PutMapping("/{id}/emails/{emailId}")
    public Map<String, Object> moveEmail(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id, @PathVariable String emailId) {
        folderService.moveEmail(me.getUser(), id, emailId);
        return Map.of("message", "Email moved to folder");
    }

    @DeleteMapping("/{id}/emails/{emailId}")
    public Map<String, Object> removeEmail(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id, @PathVariable String emailId) {
        folderService.removeEmailFromFolder(me.getUser(), emailId);
        return Map.of("message", "Email removed from folder");
    }

    @GetMapping("/{id}/emails")
    public Map<String, Object> folderEmails(@AuthenticationPrincipal UserPrincipal me, @PathVariable String id) {
        FolderService.FolderEmails result = folderService.folderEmails(me.getUser(), id);
        return Map.of("folder", result.folder(), "emails", result.emails());
    }
}
