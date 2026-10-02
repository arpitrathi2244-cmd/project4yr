package com.saferoad.controller;

import com.saferoad.controller.dto.RiskPredictionRequest;
import com.saferoad.controller.dto.RiskPredictionResponse;
import com.saferoad.entity.RiskPrediction;
import com.saferoad.entity.User;
import com.saferoad.repository.RiskPredictionRepository;
import com.saferoad.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/risk-predictions")
public class RiskPredictionController {

    private final RiskPredictionRepository riskPredictionRepository;
    private final UserRepository userRepository;

    public RiskPredictionController(
            RiskPredictionRepository riskPredictionRepository,
            UserRepository userRepository) {

        this.riskPredictionRepository = riskPredictionRepository;
        this.userRepository = userRepository;
    }

    // Create a risk prediction record
    @PostMapping
    public ResponseEntity<RiskPredictionResponse> createPrediction(
            @Valid @RequestBody RiskPredictionRequest request,
            Authentication authentication) {

        User user = getCurrentUser(authentication);

        RiskPrediction prediction = new RiskPrediction();

        prediction.setUser(user);
        prediction.setLocation(request.location());
        prediction.setLatitude(request.latitude());
        prediction.setLongitude(request.longitude());

        /*
         * Temporary risk score.
         * Actual AI model score will be connected later.
         */
        double score = calculateTemporaryRiskScore(request);

        prediction.setRiskScore(score);
        prediction.setRiskLevel(getRiskLevel(score));

        prediction.setWeatherCondition(request.weatherCondition());
        prediction.setRoadCondition(request.roadCondition());
        prediction.setTrafficLevel(request.trafficLevel());
        prediction.setTimeOfDay(request.timeOfDay());
        prediction.setDayOfWeek(request.dayOfWeek());

        prediction.setSchoolZone(request.schoolZone());
        prediction.setHillZone(request.hillZone());
        prediction.setWildlifeZone(request.wildlifeZone());

        prediction.setPredictedAt(LocalDateTime.now());

        RiskPrediction savedPrediction =
                riskPredictionRepository.save(prediction);

        return ResponseEntity.ok(toResponse(savedPrediction));
    }

    // Get current user's predictions
    @GetMapping("/my")
    public ResponseEntity<List<RiskPredictionResponse>> getMyPredictions(
            Authentication authentication) {

        User user = getCurrentUser(authentication);

        List<RiskPredictionResponse> predictions =
                riskPredictionRepository.findByUser(user)
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(predictions);
    }

    // Get prediction by ID
    @GetMapping("/{id}")
    public ResponseEntity<RiskPredictionResponse> getPrediction(
            @PathVariable Long id,
            Authentication authentication) {

        User user = getCurrentUser(authentication);

        return riskPredictionRepository.findById(id)
                .filter(prediction -> prediction.getUser().getId().equals(user.getId()))
                .map(prediction ->
                        ResponseEntity.ok(toResponse(prediction)))
                .orElseGet(() ->
                        ResponseEntity.notFound().build());
    }

    // Temporary calculation until Python AI model is connected
    private double calculateTemporaryRiskScore(
            RiskPredictionRequest request) {

        double score = 20.0;

        if ("HIGH".equalsIgnoreCase(request.trafficLevel())) {
            score += 25;
        }

        if ("BAD".equalsIgnoreCase(request.roadCondition())) {
            score += 20;
        }

        if ("RAINY".equalsIgnoreCase(request.weatherCondition())) {
            score += 15;
        }

        if (Boolean.TRUE.equals(request.schoolZone())) {
            score += 10;
        }

        if (Boolean.TRUE.equals(request.hillZone())) {
            score += 10;
        }

        if (Boolean.TRUE.equals(request.wildlifeZone())) {
            score += 10;
        }

        return Math.min(score, 100.0);
    }

    private String getRiskLevel(double score) {

        if (score >= 70) {
            return "HIGH";
        }

        if (score >= 40) {
            return "MEDIUM";
        }

        return "LOW";
    }

    private User getCurrentUser(Authentication authentication) {

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
    }

    private RiskPredictionResponse toResponse(
            RiskPrediction prediction) {

        return new RiskPredictionResponse(
                prediction.getId(),
                prediction.getLocation(),
                prediction.getLatitude(),
                prediction.getLongitude(),
                prediction.getRiskScore(),
                prediction.getRiskLevel(),
                prediction.getWeatherCondition(),
                prediction.getRoadCondition(),
                prediction.getTrafficLevel(),
                prediction.getTimeOfDay(),
                prediction.getDayOfWeek(),
                prediction.getSchoolZone(),
                prediction.getHillZone(),
                prediction.getWildlifeZone(),
                prediction.getPredictedAt()
        );
    }
}