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
@Table(name = "accident_records")
public class AccidentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // Accident kis user ne report kiya
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    // Accident location
    @Column(nullable = false, length = 255)
    private String location;


    // GPS coordinates
    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;


    // Accident ka time
    @Column(name = "accident_time", nullable = false)
    private LocalDateTime accidentTime;


    // LOW / MEDIUM / HIGH / CRITICAL
    @Column(nullable = false, length = 20)
    private String severity;


    // Weather during accident
    @Column(length = 50)
    private String weatherCondition;


    // Road condition during accident
    @Column(length = 100)
    private String roadCondition;


    // Additional accident information
    @Column(length = 1000)
    private String description;


    // Default constructor required by JPA
    public AccidentRecord() {
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


    public LocalDateTime getAccidentTime() {
        return accidentTime;
    }


    public String getSeverity() {
        return severity;
    }


    public String getWeatherCondition() {
        return weatherCondition;
    }


    public String getRoadCondition() {
        return roadCondition;
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


    public void setAccidentTime(LocalDateTime accidentTime) {
        this.accidentTime = accidentTime;
    }


    public void setSeverity(String severity) {
        this.severity = severity;
    }


    public void setWeatherCondition(String weatherCondition) {
        this.weatherCondition = weatherCondition;
    }


    public void setRoadCondition(String roadCondition) {
        this.roadCondition = roadCondition;
    }


    public void setDescription(String description) {
        this.description = description;
    }
}