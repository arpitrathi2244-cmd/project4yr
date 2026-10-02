package com.saferoad.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;


@Entity
@Table(name = "road_conditions")
public class RoadCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // Road condition kis user ne report ki
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    // Road/location information
    @Column(nullable = false, length = 255)
    private String location;


    // GPS coordinates
    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;


    // Overall road condition
    // Example: DRY, WET, DAMAGED, POTHOLES, BLOCKED
    @Column(nullable = false, length = 50)
    private String condition;


    // Traffic level
    // Example: LOW, MEDIUM, HIGH
    @Column(length = 20)
    private String trafficLevel;


    // Visibility condition
    // Example: GOOD, MODERATE, POOR
    @Column(length = 20)
    private String visibility;


    // Weather condition
    @Column(length = 50)
    private String weatherCondition;


    // Time when condition was reported
    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt;


    // Additional information
    @Column(length = 1000)
    private String description;


    // Default constructor required by JPA
    public RoadCondition() {
    }


    public Long getId() {
        return id;
    }


    public User getUser() {
        return user;
    }


    public String getLocation() {
        return location;
    }


    public Double getLatitude() {
        return latitude;
    }


    public Double getLongitude() {
        return longitude;
    }


    public String getCondition() {
        return condition;
    }


    public String getTrafficLevel() {
        return trafficLevel;
    }


    public String getVisibility() {
        return visibility;
    }


    public String getWeatherCondition() {
        return weatherCondition;
    }


    public LocalDateTime getReportedAt() {
        return reportedAt;
    }


    public String getDescription() {
        return description;
    }


    public void setId(Long id) {
        this.id = id;
    }


    public void setUser(User user) {
        this.user = user;
    }


    public void setLocation(String location) {
        this.location = location;
    }


    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }


    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }


    public void setCondition(String condition) {
        this.condition = condition;
    }


    public void setTrafficLevel(String trafficLevel) {
        this.trafficLevel = trafficLevel;
    }


    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }


    public void setWeatherCondition(String weatherCondition) {
        this.weatherCondition = weatherCondition;
    }


    public void setReportedAt(LocalDateTime reportedAt) {
        this.reportedAt = reportedAt;
    }


    public void setDescription(String description) {
        this.description = description;
    }
}