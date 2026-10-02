package com.saferoad.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saferoad.controller.dto.AccidentResponse;
import com.saferoad.entity.AccidentRecord;
import com.saferoad.entity.User;
import com.saferoad.repository.AccidentRecordRepository;
import com.saferoad.repository.UserRepository;


@RestController
@RequestMapping("/api/accidents")
public class AccidentRecordController {

    private final AccidentRecordRepository accidentRecordRepository;
    private final UserRepository userRepository;


    public AccidentRecordController(
            AccidentRecordRepository accidentRecordRepository,
            UserRepository userRepository) {

        this.accidentRecordRepository = accidentRecordRepository;
        this.userRepository = userRepository;
    }


    // ============================================================
    // CREATE ACCIDENT RECORD
    // POST /api/accidents
    // ============================================================

    @PostMapping
    public ResponseEntity<?> createAccident(
            @RequestBody AccidentRecord accidentRecord,
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


            // Accident ko current user ke saath link karo
            accidentRecord.setUser(user);


            // Database mein save karo
            AccidentRecord savedRecord =
                    accidentRecordRepository.save(accidentRecord);


            // IMPORTANT:
            // Entity directly return nahi kar rahe.
            // Safe DTO return kar rahe hain.
            AccidentResponse response =
                    AccidentResponse.fromEntity(savedRecord);


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
    // GET MY ACCIDENT RECORDS
    // GET /api/accidents/my
    // ============================================================

    @GetMapping("/my")
    public ResponseEntity<?> getMyAccidents(
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


            // Current user ke accidents
            List<AccidentRecord> accidents =
                    accidentRecordRepository.findByUser(user);


            // Har entity ko safe DTO mein convert karo
            List<AccidentResponse> response =
                    accidents.stream()
                            .map(AccidentResponse::fromEntity)
                            .toList();


            return ResponseEntity.ok(response);

        } catch (Exception exception) {

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(exception.getMessage());
        }
    }
}