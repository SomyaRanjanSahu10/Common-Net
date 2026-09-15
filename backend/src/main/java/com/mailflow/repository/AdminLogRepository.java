package com.mailflow.repository;

import com.mailflow.model.AdminLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdminLogRepository extends MongoRepository<AdminLog, String> {
    Page<AdminLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
