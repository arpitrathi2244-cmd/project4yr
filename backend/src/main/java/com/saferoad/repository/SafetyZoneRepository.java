package com.saferoad.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.saferoad.entity.SafetyZone;

public interface SafetyZoneRepository extends JpaRepository<SafetyZone, Long> {

    List<SafetyZone> findByActiveTrue();

    List<SafetyZone> findByZoneTypeIgnoreCase(String zoneType);

    List<SafetyZone> findByLocationContainingIgnoreCase(String location);

    List<SafetyZone> findByZoneTypeIgnoreCaseAndActiveTrue(String zoneType);

    Optional<SafetyZone> findBySourceAndSourceTypeAndSourceId(
            String source,
            String sourceType,
            Long sourceId);

    List<SafetyZone> findBySourceAndSourceTypeAndSourceIdIn(
            String source,
            String sourceType,
            Collection<Long> sourceIds);
}