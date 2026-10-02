package com.saferoad.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.saferoad.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    // Email lookup
    Optional<User> findByEmail(String email);

    // Case-insensitive email lookup
    Optional<User> findByEmailIgnoreCase(String email);

    // Check email
    boolean existsByEmail(String email);

    // Case-insensitive email check
    boolean existsByEmailIgnoreCase(String email);

    // Check mobile
    boolean existsByMobile(String mobile);
}