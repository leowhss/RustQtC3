package com.rustqtc3.backend.repository;

import com.rustqtc3.backend.model.Alert;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AlertRepository extends MongoRepository<Alert, String> {

    List<Alert> findTop100ByOrderByTimestampDesc();
}
