package com.saferoad.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RiskPredictionRequest(

        @NotBlank(message = "Location is required")
        String location,

        @NotNull(message = "Latitude is required")
        Double latitude,

        @NotNull(message = "Longitude is required")
        Double longitude,

        String weatherCondition,

        String roadCondition,

        String trafficLevel,

        String timeOfDay,

        String dayOfWeek,

        Boolean schoolZone,

        Boolean hillZone,

        Boolean wildlifeZone
) {
}