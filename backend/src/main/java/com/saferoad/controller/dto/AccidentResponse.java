package com.saferoad.controller.dto;

import java.time.LocalDateTime;

import com.saferoad.entity.AccidentRecord;


public record AccidentResponse(
        Long id,
        String location,
        Double latitude,
        Double longitude,
        LocalDateTime accidentTime,
        String severity,
        String weatherCondition,
        String roadCondition,
        String description
) {

    // Entity ko safe API response mein convert karta hai
    public static AccidentResponse fromEntity(
            AccidentRecord accident) {

        return new AccidentResponse(
                accident.getId(),
                accident.getLocation(),
                accident.getLatitude(),
                accident.getLongitude(),
                accident.getAccidentTime(),
                accident.getSeverity(),
                accident.getWeatherCondition(),
                accident.getRoadCondition(),
                accident.getDescription()
        );
    }
}