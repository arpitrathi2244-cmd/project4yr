package com.saferoad.controller;
import com.saferoad.service.AiPredictionService;
import com.saferoad.controller.dto.AiPredictionResponse;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiPredictionController {

    private final AiPredictionService aiPredictionService;

    public AiPredictionController(
            AiPredictionService aiPredictionService) {
        this.aiPredictionService = aiPredictionService;
    }

    @PostMapping("/predict")
    public AiPredictionResponse predict(
            @RequestBody Map<String, Object> data) {

        return aiPredictionService.predict(data);
    }
}