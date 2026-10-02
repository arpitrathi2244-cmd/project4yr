package com.saferoad.controller.dto;

import java.time.LocalDateTime;

import com.saferoad.entity.RoadCondition;


public record RoadConditionResponse(
        Long id,
        String location,
        Double latitude,
        Double longitude,
        String condition,
        String trafficLevel,
        String visibility,
        String weatherCondition,
        LocalDateTime reportedAt,
        String description
) {

    // Entity ko safe API response mein convert karta hai
    public static RoadConditionResponse fromEntity(
            RoadCondition roadCondition) {

        return new RoadConditionResponse(
                roadCondition.getId(),
                roadCondition.getLocation(),
                roadCondition.getLatitude(),
                roadCondition.getLongitude(),
                roadCondition.getCondition(),
                roadCondition.getTrafficLevel(),
                roadCondition.getVisibility(),
                roadCondition.getWeatherCondition(),
                roadCondition.getReportedAt(),
                roadCondition.getDescription()
        );
    }
}