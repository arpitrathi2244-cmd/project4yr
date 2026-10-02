package com.saferoad.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.saferoad.entity.RoadCondition;
import com.saferoad.entity.User;


public interface RoadConditionRepository
        extends JpaRepository<RoadCondition, Long> {

    // Particular user ki reported road conditions
    List<RoadCondition> findByUser(User user);

    // Location ke according road conditions
    List<RoadCondition> findByLocationContainingIgnoreCase(
            String location
    );

    // Road condition ke according records
    List<RoadCondition> findByConditionIgnoreCase(
            String condition
    );

    // Traffic level ke according records
    List<RoadCondition> findByTrafficLevelIgnoreCase(
            String trafficLevel
    );
}   