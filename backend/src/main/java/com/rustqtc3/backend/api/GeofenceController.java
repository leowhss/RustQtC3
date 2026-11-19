package com.rustqtc3.backend.api;

import com.rustqtc3.backend.model.Geofence;
import com.rustqtc3.backend.repository.GeofenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/geofences")
public class GeofenceController {

    private final GeofenceRepository geofenceRepository;

    public GeofenceController(GeofenceRepository geofenceRepository) {
        this.geofenceRepository = geofenceRepository;
    }

    @GetMapping
    public List<Geofence> list() {
        return geofenceRepository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Geofence create(@RequestBody Geofence geofence) {
        geofence.setId(null);
        return geofenceRepository.save(geofence);
    }

    @PutMapping("/{id}")
    public Geofence update(@PathVariable String id, @RequestBody Geofence geofence) {
        if (!geofenceRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Geofence not found: " + id);
        }
        geofence.setId(id);
        return geofenceRepository.save(geofence);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        geofenceRepository.deleteById(id);
    }
}
