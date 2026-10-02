package com.saferoad.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "safety_zones")
public class SafetyZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 50)
    private String zoneType;

    @Column(nullable = false, length = 255)
    private String location;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    private Double radius;

    @Column
    private Integer speedLimit;

    @Column(nullable = false)
    private Boolean active = true;

    /*
     * Controls whether safety alerts are enabled for this zone.
     * This is intentionally independent from frontend layer visibility.
     */
    @Column(name = "alert_enabled", nullable = true)
    private Boolean alertEnabled = true;

    @Column(length = 1000)
    private String description;

    /*
     * OSM identity. Used to avoid importing the same OSM object repeatedly.
     */
    @Column(name = "source", length = 40)
    private String source;

    @Column(name = "source_type", length = 20)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    /*
     * Optional GeoJSON-like coordinate data stored as JSON text.
     * Schools normally have no geometry; protected areas may have polygons.
     */
    @Column(name = "geometry_json", columnDefinition = "TEXT")
    private String geometryJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public SafetyZone() {
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getZoneType() { return zoneType; }
    public String getLocation() { return location; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getRadius() { return radius; }
    public Integer getSpeedLimit() { return speedLimit; }
    public Boolean getActive() { return active; }
    public Boolean getAlertEnabled() { return alertEnabled; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getSource() { return source; }
    public String getSourceType() { return sourceType; }
    public Long getSourceId() { return sourceId; }
    public String getGeometryJson() { return geometryJson; }

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setZoneType(String zoneType) { this.zoneType = zoneType; }
    public void setLocation(String location) { this.location = location; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public void setRadius(Double radius) { this.radius = radius; }
    public void setSpeedLimit(Integer speedLimit) { this.speedLimit = speedLimit; }
    public void setActive(Boolean active) { this.active = active; }
    public void setAlertEnabled(Boolean alertEnabled) { this.alertEnabled = alertEnabled; }
    public void setDescription(String description) { this.description = description; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setSource(String source) { this.source = source; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public void setGeometryJson(String geometryJson) { this.geometryJson = geometryJson; }
}
