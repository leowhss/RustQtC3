package com.rustqtc3.backend.repository;

import com.rustqtc3.backend.model.Track;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TrackRepository extends MongoRepository<Track, String> {
}
