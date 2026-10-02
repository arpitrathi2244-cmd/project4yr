package com.saferoad.controller.dto;

import java.time.LocalDateTime;

import com.saferoad.entity.SafetyZone;

public record SafetyZoneResponse(
        Long id,
        String name,
        String zoneType,
        String location,
        Double latitude,
        Double longitude,
        Double radius,
        Integer speedLimit,
        Boolean active,
        Boolean alertEnabled,
        String description,
        String source,
        String sourceType,
        Long sourceId,
        String geometryJson,
        LocalDateTime createdAt
) {
    public static SafetyZoneResponse fromEntity(SafetyZone zone) {
        return new SafetyZoneResponse(
                zone.getId(),
                zone.getName(),
                zone.getZoneType(),
                zone.getLocation(),
                zone.getLatitude(),
                zone.getLongitude(),
                zone.getRadius(),
                zone.getSpeedLimit(),
                zone.getActive(),
                zone.getAlertEnabled(),
                zone.getDescription(),
                zone.getSource(),
                zone.getSourceType(),
                zone.getSourceId(),
                zone.getGeometryJson(),
                zone.getCreatedAt()
        );
    }
}
