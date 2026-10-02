package com.saferoad.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.saferoad.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * FILE: JwtAuthenticationFilter.java
 *
 * PURPOSE:
 * Reads, validates and authenticates JWT tokens.
 *
 * JWT contains:
 * - Email
 * - Role
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // Read Authorization header.
        String authorizationHeader =
                request.getHeader("Authorization");

        /*
         * No Bearer token:
         * Continue the request without authentication.
         */
        if (authorizationHeader == null
                || !authorizationHeader.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        // Extract JWT token.
        String token = authorizationHeader.substring(7);

        try {

            // Validate JWT.
            if (jwtService.isTokenValid(token)) {

                // Extract email and role.
                String email = jwtService.extractEmail(token);
                String role = jwtService.extractRole(token);

                /*
                 * Convert USER / ADMIN into Spring Security
                 * authorities.
                 *
                 * ROLE_USER
                 * ROLE_ADMIN
                 */
                SimpleGrantedAuthority authority =
                        new SimpleGrantedAuthority(
                                "ROLE_" + role
                        );

                /*
                 * Create authenticated user.
                 */
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                email,
                                null,
                                List.of(authority)
                        );

                // Store authentication for this request.
                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authentication);
            }

        } catch (Exception exception) {

            /*
             * Invalid JWT does not crash the application.
             * The request continues without authentication.
             */
        }

        // Continue the filter chain.
        filterChain.doFilter(request, response);
    }
}