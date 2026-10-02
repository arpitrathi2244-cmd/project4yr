package com.saferoad.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saferoad.controller.dto.ChangePasswordRequest;
import com.saferoad.controller.dto.UpdateProfileRequest;
import com.saferoad.entity.User;
import com.saferoad.repository.UserRepository;
import com.saferoad.service.AuthService;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;
    private final AuthService authService;


    public UserController(
        UserRepository userRepository,
        AuthService authService) {

    this.userRepository = userRepository;
    this.authService = authService;
}


    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Authentication authentication) {

        // JWT filter se logged-in user's email milega
        String email = authentication.getName();


        // Database se user find karo
        User user = userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found")
                );


        // Password return nahi karna hai
        return ResponseEntity.ok(
                new ProfileResponse(
                        user.getId(),
                        user.getFullName(),
                        user.getAge(),
                        user.getMobile(),
                        user.getEmail(),
                        user.getRole()
                )
        );
    }
    @PutMapping("/profile")
public ResponseEntity<?> updateProfile(
        @Valid @RequestBody UpdateProfileRequest request,
        Authentication authentication) {

    // JWT se logged-in user's email
    String email = authentication.getName();

    // Database se current user find karo
    User user = userRepository
            .findByEmailIgnoreCase(email)
            .orElseThrow(() ->
                    new RuntimeException("User not found")
            );

    // Sirf wahi fields update karo jo request mein di gayi hain
    if (request.fullName() != null &&
            !request.fullName().trim().isEmpty()) {

        user.setFullName(request.fullName().trim());
    }

    if (request.age() != null) {
        user.setAge(request.age());
    }

    if (request.mobile() != null &&
            !request.mobile().trim().isEmpty()) {

        user.setMobile(request.mobile().trim());
    }

    if (request.email() != null &&
            !request.email().trim().isEmpty()) {

        user.setEmail(request.email().trim().toLowerCase());
    }

    // Updated user save karo
    User updatedUser = userRepository.save(user);

    // Password return nahi karna
    return ResponseEntity.ok(
            new ProfileResponse(
                    updatedUser.getId(),
                    updatedUser.getFullName(),
                    updatedUser.getAge(),
                    updatedUser.getMobile(),
                    updatedUser.getEmail(),
                    updatedUser.getRole()
            )
    );
}


    // Profile API ka safe response
    public record ProfileResponse(
            Long id,
            String fullName,
            Integer age,
            String mobile,
            String email,
            String role
    ) {
    }
    @PutMapping("/change-password")
public ResponseEntity<?> changePassword(
        @Valid @RequestBody ChangePasswordRequest request,
        Authentication authentication) {

    try {
        String email = authentication.getName();

        userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        authService.changePassword(
                email,
                request.currentPassword(),
                request.newPassword()
        );

        return ResponseEntity.ok(
                new MessageResponse("Password changed successfully")
        );

    } catch (IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new MessageResponse(exception.getMessage()));
    }
}

public record MessageResponse(String message) {}
}