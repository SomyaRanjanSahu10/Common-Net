package com.mailflow.service;

import com.mailflow.dto.EmailDto;
import com.mailflow.exception.ApiException;
import com.mailflow.model.Email;
import com.mailflow.model.Folder;
import com.mailflow.model.User;
import com.mailflow.repository.EmailRepository;
import com.mailflow.repository.FolderRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/** Direct port of server/routes/folders.js. */
@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final EmailRepository emailRepository;
    private final EmailMapper emailMapper;
    private final MongoTemplate mongoTemplate;

    public FolderService(FolderRepository folderRepository, EmailRepository emailRepository,
                          EmailMapper emailMapper, MongoTemplate mongoTemplate) {
        this.folderRepository = folderRepository;
        this.emailRepository = emailRepository;
        this.emailMapper = emailMapper;
        this.mongoTemplate = mongoTemplate;
    }

    public List<Folder> list(User owner) {
        return folderRepository.findByOwnerOrderByCreatedAtDesc(owner.getId());
    }

    public Folder create(User owner, String name, String color) {
        if (name == null || name.trim().isEmpty())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Folder name is required");
        if (folderRepository.existsByOwnerAndName(owner.getId(), name.trim()))
            throw new ApiException(HttpStatus.CONFLICT, "Folder name already exists");
        Folder folder = Folder.builder().owner(owner.getId()).name(name.trim())
                .color(color == null || color.isBlank() ? "#0078d4" : color).build();
        return folderRepository.save(folder);
    }

    private Folder findOwned(User owner, String id) {
        Folder folder = folderRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Folder not found"));
        if (!folder.getOwner().equals(owner.getId())) throw new ApiException(HttpStatus.NOT_FOUND, "Folder not found");
        return folder;
    }

    public Folder update(User owner, String id, String name, String color) {
        Folder folder = findOwned(owner, id);
        if (name != null && !name.isBlank()) folder.setName(name.trim());
        if (color != null && !color.isBlank()) folder.setColor(color);
        try {
            return folderRepository.save(folder);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "Folder name already exists");
        }
    }

    public void delete(User owner, String id) {
        Folder folder = findOwned(owner, id);
        mongoTemplate.updateMulti(Query.query(where("folder").is(folder.getId())), Update.update("folder", null), Email.class);
        folderRepository.delete(folder);
    }

    public void moveEmail(User owner, String folderId, String emailId) {
        Folder folder = findOwned(owner, folderId);
        Email email = emailRepository.findById(emailId)
                .filter(e -> owner.getId().equals(e.getSender()) || owner.getId().equals(e.getReceiver()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Email not found"));
        email.setFolder(folder.getId());
        emailRepository.save(email);
    }

    public void removeEmailFromFolder(User owner, String emailId) {
        Email email = emailRepository.findById(emailId)
                .filter(e -> owner.getId().equals(e.getSender()) || owner.getId().equals(e.getReceiver()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Email not found"));
        email.setFolder(null);
        emailRepository.save(email);
    }

    public record FolderEmails(Folder folder, List<EmailDto> emails) {}

    public FolderEmails folderEmails(User owner, String folderId) {
        Folder folder = findOwned(owner, folderId);
        Query q = Query.query(
                where("folder").is(folder.getId()).andOperator(
                        new org.springframework.data.mongodb.core.query.Criteria().orOperator(
                                where("sender").is(owner.getId()).and("deletedBySender").is(false),
                                where("receiver").is(owner.getId()).and("deletedByReceiver").is(false)
                        )
                )
        ).with(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Email> emails = mongoTemplate.find(q, Email.class);
        return new FolderEmails(folder, emailMapper.toDtoList(emails));
    }
}
