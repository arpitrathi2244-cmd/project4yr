package com.saferoad.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.saferoad.entity.AccidentRecord;
import com.saferoad.entity.User;


public interface AccidentRecordRepository
        extends JpaRepository<AccidentRecord, Long> {

    // Particular user ke saare accident records
    List<AccidentRecord> findByUser(User user);

    // Severity ke according accidents
    List<AccidentRecord> findBySeverityIgnoreCase(String severity);

    // Location ke according accidents
    List<AccidentRecord> findByLocationContainingIgnoreCase(
            String location
    );
} 