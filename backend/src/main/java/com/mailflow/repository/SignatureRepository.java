package com.mailflow.repository;

import com.mailflow.model.Signature;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SignatureRepository extends MongoRepository<Signature, String> {
    Optional<Signature> findByUser(String userId);
    void deleteByUser(String userId);
}
