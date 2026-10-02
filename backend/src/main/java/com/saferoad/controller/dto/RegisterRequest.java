package com.saferoad.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * FILE: RegisterRequest.java
 *
 * PURPOSE:
 * Request DTO used when a new user creates a SafeRoad account.
 *
 * This class receives registration data from the frontend.
 *
 * IMPORTANT:
 * DTO is separate from the User entity so that we do not expose
 * database fields directly through the API.
 */
public class RegisterRequest {

    /**
     * User's full name.
     */
    @NotBlank(message = "Full name is required")
    @Size(
        min = 2,
        max = 120,
        message = "Full name must contain between 2 and 120 characters"
    )
    private String fullName;

    /**
     * User's age.
     */
    @NotNull(message = "Age is required")
    @Min(value = 1, message = "Age must be at least 1")
    @Max(value = 120, message = "Age must not be greater than 120")
    private Integer age;

    /**
     * User's 10-digit mobile number.
     */
    @NotBlank(message = "Mobile number is required")
    @Pattern(
        regexp = "^[0-9]{10}$",
        message = "Mobile number must contain exactly 10 digits"
    )
    private String mobile;

    /**
     * User's email address.
     */
    @NotBlank(message = "Email address is required")
    @Email(message = "Please enter a valid email address")
    @Size(
        max = 180,
        message = "Email address is too long"
    )
    private String email;

    /**
     * Password received from the registration form.
     *
     * The service layer will hash this password using BCrypt
     * before saving it to PostgreSQL.
     */
    @NotBlank(message = "Password is required")
    @Size(
        min = 8,
        max = 100,
        message = "Password must contain between 8 and 100 characters"
    )
    private String password;

    /**
     * Default constructor required by Spring/Jackson.
     */
    public RegisterRequest() {
    }

    // ---------------------------------------------------------
    // GETTERS
    // ---------------------------------------------------------

    public String getFullName() {
        return fullName;
    }

    public Integer getAge() {
        return age;
    }

    public String getMobile() {
        return mobile;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    // ---------------------------------------------------------
    // SETTERS
    // ---------------------------------------------------------

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}