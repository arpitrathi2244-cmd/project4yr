package com.saferoad.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saferoad.controller.dto.RegisterRequest;
import com.saferoad.entity.User;
import com.saferoad.repository.UserRepository;

/**
 * FILE: AuthService.java
 *
 * PURPOSE:
 * Contains the business logic related to user authentication.
 *
 * CURRENT RESPONSIBILITY:
 * - Validate duplicate email
 * - Validate duplicate mobile number
 * - Hash the user's password
 * - Create and save a new User
 *
 * JWT login will be added in the next authentication step.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /**
     * Constructor injection keeps dependencies explicit
     * and makes the service easier to test.
     */
   
public AuthService(UserRepository userRepository,
                   PasswordEncoder passwordEncoder,
                   JwtService jwtService) {

    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
}
    /**
     * Register a new SafeRoad user.
     *
     * @param request registration data received from the frontend
     * @return saved User entity
     */
    @Transactional
    public User registerUser(RegisterRequest request) {

        // -----------------------------------------------------
        // 1. Check whether the email is already registered.
        // -----------------------------------------------------

        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {

            throw new IllegalArgumentException(
                "Email address is already registered"
            );
        }


        // -----------------------------------------------------
        // 2. Check whether the mobile number is already registered.
        // -----------------------------------------------------

        if (userRepository.existsByMobile(request.getMobile())) {

            throw new IllegalArgumentException(
                "Mobile number is already registered"
            );
        }


        // -----------------------------------------------------
        // 3. Create a new User entity.
        // -----------------------------------------------------

        User user = new User();

        user.setFullName(request.getFullName().trim());
        user.setAge(request.getAge());
        user.setMobile(request.getMobile());
        user.setEmail(request.getEmail().trim().toLowerCase());


        // -----------------------------------------------------
        // 4. Hash the password.
        //
        // NEVER store the original password in PostgreSQL.
        // BCrypt is provided by Spring Security.
        // -----------------------------------------------------

        String hashedPassword =
            passwordEncoder.encode(request.getPassword());

        user.setPassword(hashedPassword);


        // -----------------------------------------------------
        // 5. Initial verification state.
        //
        // Actual OTP verification will be implemented later.
        // Therefore both remain false for now.
        // -----------------------------------------------------

        user.setMobileVerified(false);
        user.setEmailVerified(false);

        user.setActive(true);
        user.setRole("USER");


        // -----------------------------------------------------
        // 6. Save the user in PostgreSQL.
        // -----------------------------------------------------

        return userRepository.save(user);
    }
    public User loginUser(String email, String password) {

    User user = userRepository.findByEmailIgnoreCase(email.trim())
            .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

    if (!user.getActive()) {
        throw new IllegalArgumentException("User account is inactive");
    }

    if (!passwordEncoder.matches(password, user.getPassword())) {
        throw new IllegalArgumentException("Invalid email or password");
    }

    return user;
}
public void changePassword(String email, String currentPassword, String newPassword) {

    // Find logged-in user by email
    User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    // Check current password
    if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
        throw new IllegalArgumentException("Current password is incorrect");
    }

    // Prevent using the same password again
    if (passwordEncoder.matches(newPassword, user.getPassword())) {
        throw new IllegalArgumentException(
                "New password must be different from current password"
        );
    }

    // Hash the new password before saving
    String hashedPassword = passwordEncoder.encode(newPassword);

    user.setPassword(hashedPassword);

    // Save updated password
    userRepository.save(user);
}
}
