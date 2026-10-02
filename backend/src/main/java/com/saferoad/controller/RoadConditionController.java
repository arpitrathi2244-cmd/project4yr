package com.saferoad.controller;

import com.saferoad.controller.dto.RoadConditionResponse;
import com.saferoad.entity.RoadCondition;
import com.saferoad.entity.User;
import com.saferoad.repository.RoadConditionRepository;
import com.saferoad.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/road-conditions")
public class RoadConditionController {

    private final RoadConditionRepository roadConditionRepository;
    private final UserRepository userRepository;


    public RoadConditionController(
            RoadConditionRepository roadConditionRepository,
            UserRepository userRepository) {

        this.roadConditionRepository = roadConditionRepository;
        this.userRepository = userRepository;
    }


    // ============================================================
    // CREATE ROAD CONDITION REPORT
    // POST /api/road-conditions
    // ============================================================

    @PostMapping
    public ResponseEntity<?> createRoadCondition(
            @RequestBody RoadCondition roadCondition,
            Authentication authentication) {

        try {

            // JWT se logged-in user's email
            String email = authentication.getName();


            // Database se current user find karo
            User user = userRepository
                    .findByEmailIgnoreCase(email)
                    .orElseThrow(() ->
                            new RuntimeException("User not found")
                    );


            // Report ko current user ke saath link karo
            roadCondition.setUser(user);


            // Database mein save karo
            RoadCondition savedCondition =
                    roadConditionRepository.save(roadCondition);


            // Safe response DTO
            RoadConditionResponse response =
                    RoadConditionResponse.fromEntity(savedCondition);


            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(response);

        } catch (Exception exception) {

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(exception.getMessage());
        }
    }


    // ============================================================
    // GET MY ROAD CONDITION REPORTS
    // GET /api/road-conditions/my
    // ============================================================

    @GetMapping("/my")
    public ResponseEntity<?> getMyRoadConditions(
            Authentication authentication) {

        try {

            // JWT se logged-in user's email
            String email = authentication.getName();


            // Current user find karo
            User user = userRepository
                    .findByEmailIgnoreCase(email)
                    .orElseThrow(() ->
                            new RuntimeException("User not found")
                    );


            // Current user's reports
            List<RoadCondition> conditions =
                    roadConditionRepository.findByUser(user);


            // Safe DTO list
            List<RoadConditionResponse> response =
                    conditions.stream()
                            .map(RoadConditionResponse::fromEntity)
                            .toList();


            return ResponseEntity.ok(response);

        } catch (Exception exception) {

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(exception.getMessage());
        }
    }
}