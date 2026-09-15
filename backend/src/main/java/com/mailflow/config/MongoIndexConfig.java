package com.mailflow.config;

import com.mailflow.model.Email;
import com.mailflow.model.Folder;
import jakarta.annotation.PostConstruct;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;

    public MongoIndexConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void initIndexes() {

        // Email text index on subject and body
        IndexOperations emailOps = mongoTemplate.indexOps(Email.class);

        TextIndexDefinition textIndex = TextIndexDefinition.builder()
                .onField("subject")
                .onField("body")
                .build();

        emailOps.ensureIndex(textIndex);

        // Folder compound unique index on owner + name
        IndexOperations folderOps = mongoTemplate.indexOps(Folder.class);

        IndexDefinition ownerNameIndex =
                new CompoundIndexDefinition(
                        new org.bson.Document(
                                Map.of(
                                        "owner", 1,
                                        "name", 1
                                )
                        )
                ).unique();

        folderOps.ensureIndex(ownerNameIndex);
    }
}