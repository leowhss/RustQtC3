package com.rustqtc3.backend.repository;

import com.rustqtc3.backend.model.TrackHistoryPoint;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TrackHistoryRepository extends MongoRepository<TrackHistoryPoint, String> {

    List<TrackHistoryPoint> findByTrackIdOrderByTimestampAsc(String trackId);
}
