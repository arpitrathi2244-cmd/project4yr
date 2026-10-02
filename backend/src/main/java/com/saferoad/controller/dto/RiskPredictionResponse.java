package com.saferoad.controller.dto;

import java.time.LocalDateTime;

public record RiskPredictionResponse(

        Long id,

        String location,

        Double latitude,

        Double longitude,

        Double riskScore,

        String riskLevel,

        String weatherCondition,

        String roadCondition,

        String trafficLevel,

        String timeOfDay,

        String dayOfWeek,

        Boolean schoolZone,

        Boolean hillZone,

        Boolean wildlifeZone,

        LocalDateTime predictedAt
) {
}