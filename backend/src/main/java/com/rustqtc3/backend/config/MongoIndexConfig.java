package com.rustqtc3.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Ensures the track_history TTL index matches the configured retention window.
 * Spring Data's @Indexed(expireAfterSeconds=...) is fixed at compile time, so the
 * TTL is instead applied here from application.yml at startup.
 */
@Component
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;
    private final long retentionSeconds;

    public MongoIndexConfig(MongoTemplate mongoTemplate,
                             @Value("${c3.history.retention-hours:48}") long retentionHours) {
        this.mongoTemplate = mongoTemplate;
        this.retentionSeconds = TimeUnit.HOURS.toSeconds(retentionHours);
    }

    @PostConstruct
    public void ensureTtlIndex() {
        IndexOperations indexOps = mongoTemplate.indexOps("track_history");
        indexOps.ensureIndex(
                new Index().on("timestamp", org.springframework.data.domain.Sort.Direction.ASC)
                        .named("timestamp_ttl")
                        .expire(retentionSeconds, TimeUnit.SECONDS));
    }
}
