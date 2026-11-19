package com.rustqtc3.backend.repository;

import com.rustqtc3.backend.model.Geofence;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface GeofenceRepository extends MongoRepository<Geofence, String> {
}
