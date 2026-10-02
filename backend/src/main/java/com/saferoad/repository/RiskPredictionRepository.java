package com.saferoad.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.saferoad.entity.RiskPrediction;
import com.saferoad.entity.User;

public interface RiskPredictionRepository extends JpaRepository<RiskPrediction, Long> {

    // Current user ki saari predictions
    List<RiskPrediction> findByUser(User user);

    // Risk level ke basis par predictions
    List<RiskPrediction> findByRiskLevelIgnoreCase(String riskLevel);

    // Location ke basis par predictions
    List<RiskPrediction> findByLocationContainingIgnoreCase(String location);
}