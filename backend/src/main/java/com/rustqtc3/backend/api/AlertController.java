package com.rustqtc3.backend.api;

import com.rustqtc3.backend.model.Alert;
import com.rustqtc3.backend.repository.AlertRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRepository alertRepository;

    public AlertController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @GetMapping
    public List<Alert> list() {
        return alertRepository.findTop100ByOrderByTimestampDesc();
    }

    @PutMapping("/{id}/ack")
    public Alert acknowledge(@PathVariable String id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found: " + id));
        alert.setAcknowledged(true);
        return alertRepository.save(alert);
    }
}
