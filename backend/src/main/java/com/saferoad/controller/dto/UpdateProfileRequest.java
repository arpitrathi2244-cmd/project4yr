package com.saferoad.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @Size(max = 100, message = "Full name is too long")
        String fullName,

        Integer age,

        @Pattern(
                regexp = "^\\d{10}$",
                message = "Mobile number must contain exactly 10 digits"
        )
        String mobile,

        @Email(message = "Invalid email address")
        String email
) {
}