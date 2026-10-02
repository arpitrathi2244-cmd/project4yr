package com.saferoad.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FILE: HealthController.java
 *
 * PURPOSE:
 * Simple endpoint used to verify that the Spring Boot backend
 * is running correctly.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> healthCheck() {

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("status", "UP");
        response.put("service", "SafeRoad AI Backend");
        response.put("timestamp", Instant.now().toString());

        return response;
    }
}
