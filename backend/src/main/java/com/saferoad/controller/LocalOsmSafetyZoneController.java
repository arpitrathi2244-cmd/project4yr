package com.saferoad.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saferoad.service.LocalOsmSafetyZoneService;

@RestController
@RequestMapping("/api/safety-zones")
@CrossOrigin(origins = "*")
public class LocalOsmSafetyZoneController {

    private final LocalOsmSafetyZoneService localOsmSafetyZoneService;

    public LocalOsmSafetyZoneController(LocalOsmSafetyZoneService localOsmSafetyZoneService) {
        this.localOsmSafetyZoneService = localOsmSafetyZoneService;
    }

    @org.springframework.web.bind.annotation.GetMapping("/local-nearby")
    public ResponseEntity<?> findNearby(
            @org.springframework.web.bind.annotation.RequestParam double latitude,
            @org.springframework.web.bind.annotation.RequestParam double longitude,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10000") double radius) {
        try {
            return ResponseEntity.ok(localOsmSafetyZoneService.findNearby(latitude, longitude, radius));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Local OSM nearby query failed", "message", ex.getMessage()));
        }
    }

    @PostMapping("/local-route")
    public ResponseEntity<?> findZonesAlongRoute(@RequestBody RouteRequest request) {
        try {
            double radius = request.radiusMeters() == null ? 1200.0 : request.radiusMeters();
            return ResponseEntity.ok(
                    localOsmSafetyZoneService.findAlongRoute(request.coordinates(), radius));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Local OSM query failed", "message", ex.getMessage()));
        }
    }

    public record RouteRequest(
            List<List<Double>> coordinates,
            Double radiusMeters) {
    }
}
