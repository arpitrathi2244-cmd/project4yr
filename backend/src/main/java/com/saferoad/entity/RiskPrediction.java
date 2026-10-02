package com.saferoad.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "risk_predictions")
public class RiskPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Prediction kis user ke liye hai
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Location details
    @Column(nullable = false)
    private String location;

    private Double latitude;

    private Double longitude;

    // AI risk score: 0 - 100
    private Double riskScore;

    // LOW / MEDIUM / HIGH
    @Column(nullable = false)
    private String riskLevel;

    // Input conditions
    private String weatherCondition;

    private String roadCondition;

    private String trafficLevel;

    private String timeOfDay;

    private String dayOfWeek;

    private Boolean schoolZone;

    private Boolean hillZone;

    private Boolean wildlifeZone;

    private LocalDateTime predictedAt;

    // ---------------- GETTERS & SETTERS ----------------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Double riskScore) {
        this.riskScore = riskScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getWeatherCondition() {
        return weatherCondition;
    }

    public void setWeatherCondition(String weatherCondition) {
        this.weatherCondition = weatherCondition;
    }

    public String getRoadCondition() {
        return roadCondition;
    }

    public void setRoadCondition(String roadCondition) {
        this.roadCondition = roadCondition;
    }

    public String getTrafficLevel() {
        return trafficLevel;
    }

    public void setTrafficLevel(String trafficLevel) {
        this.trafficLevel = trafficLevel;
    }

    public String getTimeOfDay() {
        return timeOfDay;
    }

    public void setTimeOfDay(String timeOfDay) {
        this.timeOfDay = timeOfDay;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public Boolean getSchoolZone() {
        return schoolZone;
    }

    public void setSchoolZone(Boolean schoolZone) {
        this.schoolZone = schoolZone;
    }

    public Boolean getHillZone() {
        return hillZone;
    }

    public void setHillZone(Boolean hillZone) {
        this.hillZone = hillZone;
    }

    public Boolean getWildlifeZone() {
        return wildlifeZone;
    }

    public void setWildlifeZone(Boolean wildlifeZone) {
        this.wildlifeZone = wildlifeZone;
    }

    public LocalDateTime getPredictedAt() {
        return predictedAt;
    }

    public void setPredictedAt(LocalDateTime predictedAt) {
        this.predictedAt = predictedAt;
    }
}