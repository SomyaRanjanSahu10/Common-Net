package com.mailflow.repository;

import com.mailflow.model.Folder;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends MongoRepository<Folder, String> {
    List<Folder> findByOwnerOrderByCreatedAtDesc(String owner);
    Optional<Folder> findByOwnerAndName(String owner, String name);
    boolean existsByOwnerAndName(String owner, String name);
}
