package com.saferoad.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saferoad.controller.UserController.MessageResponse;
import com.saferoad.controller.dto.LoginRequest;
import com.saferoad.controller.dto.RegisterRequest;
import com.saferoad.entity.User;
import com.saferoad.service.AuthService;
import com.saferoad.service.JwtService;

import jakarta.validation.Valid;

/**
 * FILE: AuthController.java
 *
 * PURPOSE:
 * REST controller for SafeRoad user authentication.
 *
 * APIs:
 * POST /api/auth/register
 * POST /api/auth/login
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    /**
     * Constructor injection.
     */
    public AuthController(
            AuthService authService,
            JwtService jwtService) {

        this.authService = authService;
        this.jwtService = jwtService;
    }

    /**
     * =========================================================
     * USER REGISTRATION
     * =========================================================
     *
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(
            @Valid @RequestBody RegisterRequest request) {

        try {

            User user = authService.registerUser(request);

            /*
             * Password is never returned to the frontend.
             */
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new RegistrationResponse(
                            user.getId(),
                            user.getFullName(),
                            user.getEmail(),
                            user.getMobile(),
                            user.getMobileVerified(),
                            user.getEmailVerified()
                    ));

        } catch (IllegalArgumentException exception) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            exception.getMessage()
                    ));
        }
    }

    /**
     * =========================================================
     * USER LOGIN
     * =========================================================
     *
     * POST /api/auth/login
     *
     * Successful login generates a JWT token.
     */
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(
            @Valid @RequestBody LoginRequest request) {

        try {

            User user = authService.loginUser(
                    request.getEmail(),
                    request.getPassword()
            );

            // Generate JWT token after successful login.
            String token = jwtService.generateToken(
        user.getEmail(),
        user.getRole()
);

            return ResponseEntity.ok(
                    new LoginResponse(
                            user.getId(),
                            user.getFullName(),
                            user.getEmail(),
                            user.getMobile(),
                            token
                    )
            );

        } catch (IllegalArgumentException exception) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(
                            exception.getMessage()
                    ));
        }

    }

    @PostMapping("/logout")
public ResponseEntity<?> logoutUser() {

    return ResponseEntity.ok(
            new MessageResponse("Logout successful")
    );
}

    /**
     * =========================================================
     * REGISTRATION RESPONSE
     * =========================================================
     */
    public record RegistrationResponse(
            Long id,
            String fullName,
            String email,
            String mobile,
            Boolean mobileVerified,
            Boolean emailVerified
    ) {
    }

    /**
     * =========================================================
     * LOGIN RESPONSE
     * =========================================================
     */
    public record LoginResponse(
            Long id,
            String fullName,
            String email,
            String mobile,
            String token
    ) {
    }

    /**
     * =========================================================
     * ERROR RESPONSE
     * =========================================================
     */
    public record ErrorResponse(
            String message
    ) {
    }
}