package com.saferoad.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * FILE: JwtService.java
 *
 * PURPOSE:
 * Handles JWT token generation and validation.
 *
 * JWT contains:
 * - Email
 * - User role
 */
@Service
public class JwtService {

    /*
     * Development secret key.
     *
     * IMPORTANT:
     * Production mein ise environment variable / secret
     * ke andar rakhenge.
     */
    private static final String SECRET_KEY =
            "SafeRoadAIProjectSecretKey2026ForJWTAuthentication123456";

    // JWT token validity: 24 hours.
    private static final long EXPIRATION_TIME =
            1000L * 60 * 60 * 24;

    /**
     * Creates the signing key used by JWT.
     */
    private SecretKey getSigningKey() {

        return Keys.hmacShaKeyFor(
                SECRET_KEY.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Generate JWT token for a user.
     *
     * @param email user's email
     * @param role user's role
     * @return generated JWT token
     */
    public String generateToken(String email, String role) {

        Date now = new Date();

        Date expiration = new Date(
                now.getTime() + EXPIRATION_TIME
        );

        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extract email from JWT token.
     */
    public String extractEmail(String token) {

        return getClaims(token).getSubject();
    }

    /**
     * Extract role from JWT token.
     */
    public String extractRole(String token) {

        return getClaims(token).get("role", String.class);
    }

    /**
     * Validate JWT token.
     *
     * This verifies:
     * - Token signature
     * - Token expiration
     */
    public boolean isTokenValid(String token) {

        try {

            getClaims(token);

            return true;

        } catch (Exception exception) {

            return false;
        }
    }

    /**
     * Parse JWT and return its claims.
     */
    private Claims getClaims(String token) {

        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}