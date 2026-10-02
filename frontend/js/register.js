/* =========================================================
   FILE: register.js
   PROJECT: AI Road Safety & Accident Prevention System

   PURPOSE:
   - Validate registration form
   - Generate/validate captcha
   - Register user through the backend

   Email OTP verification has been completely removed.
   ========================================================= */

const API_BASE_URL = "http://localhost:8080/api";

const registerForm = document.getElementById("registerForm");
const fullNameInput = document.getElementById("fullName");
const ageInput = document.getElementById("age");
const mobileInput = document.getElementById("mobile");
const emailInput = document.getElementById("email");
const passwordInput = document.getElementById("password");
const confirmPasswordInput = document.getElementById("confirmPassword");
const captchaInput = document.getElementById("captchaInput");
const captchaBox = document.getElementById("captchaBox");
const messageBox = document.getElementById("message");
const refreshCaptchaButton = document.getElementById("refreshCaptcha");
const registerButton = document.getElementById("registerBtn");

let generatedCaptcha = "";

function showMessage(message, type = "") {
    if (!messageBox) return;
    messageBox.textContent = message;
    messageBox.className = "message" + (type ? ` ${type}` : "");
}

function generateCaptcha() {
    const characters = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    let captcha = "";

    for (let i = 0; i < 6; i++) {
        captcha += characters.charAt(
            Math.floor(Math.random() * characters.length)
        );
    }

    generatedCaptcha = captcha;

    if (captchaBox) {
        captchaBox.textContent = generatedCaptcha;
    }

    if (captchaInput) {
        captchaInput.value = "";
    }
}

async function postJson(url, body) {
    const response = await fetch(url, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(body)
    });

    const text = await response.text();
    let data = {};

    if (text) {
        try {
            data = JSON.parse(text);
        } catch (error) {
            data = { message: text };
        }
    }

    return {
        ok: response.ok,
        status: response.status,
        ...data
    };
}

function validateRegistration() {
    const fullName = fullNameInput?.value.trim() || "";
    const age = Number(ageInput?.value);
    const mobile = mobileInput?.value.trim() || "";
    const email = emailInput?.value.trim() || "";
    const password = passwordInput?.value || "";
    const confirmPassword = confirmPasswordInput?.value || "";
    const captcha = captchaInput?.value.trim().toUpperCase() || "";

    if (fullName.length < 2 || fullName.length > 120) {
        showMessage("Full name must contain between 2 and 120 characters.", "error");
        fullNameInput?.focus();
        return false;
    }

    if (!Number.isInteger(age) || age < 1 || age > 120) {
        showMessage("Age must be between 1 and 120.", "error");
        ageInput?.focus();
        return false;
    }

    if (!/^\d{10}$/.test(mobile)) {
        showMessage("Mobile number must contain exactly 10 digits.", "error");
        mobileInput?.focus();
        return false;
    }

    if (!/^\S+@\S+\.\S+$/.test(email) || email.length > 180) {
        showMessage("Please enter a valid email address.", "error");
        emailInput?.focus();
        return false;
    }

    if (password.length < 8 || password.length > 100) {
        showMessage("Password must contain between 8 and 100 characters.", "error");
        passwordInput?.focus();
        return false;
    }

    if (password !== confirmPassword) {
        showMessage("Passwords do not match.", "error");
        confirmPasswordInput?.focus();
        return false;
    }

    if (captcha !== generatedCaptcha) {
        showMessage("Captcha does not match. Please try again.", "error");
        captchaInput?.focus();
        return false;
    }

    return true;
}

async function submitRegistration(event) {
    if (event) event.preventDefault();

    if (!validateRegistration()) return;

    const requestBody = {
        fullName: fullNameInput.value.trim(),
        age: Number(ageInput.value),
        mobile: mobileInput.value.trim(),
        email: emailInput.value.trim().toLowerCase(),
        password: passwordInput.value
    };

    registerButton.disabled = true;
    registerButton.textContent = "Creating Account...";
    showMessage("", "");

    try {
        const result = await postJson(
            `${API_BASE_URL}/auth/register`,
            requestBody
        );

        if (!result.ok) {
            showMessage(
                result.message || `Registration failed (HTTP ${result.status}).`,
                "error"
            );
            return;
        }

        showMessage(
            result.message || "Account created successfully. Redirecting to Sign In...",
            "success"
        );

        setTimeout(() => {
            window.location.href = "sign-in.html";
        }, 1000);
    } catch (error) {
        console.error("Registration Error:", error);
        showMessage(
            "Cannot connect to backend. Make sure Spring Boot is running on port 8080.",
            "error"
        );
    } finally {
        registerButton.disabled = false;
        registerButton.textContent = "Register →";
    }
}

if (mobileInput) {
    mobileInput.addEventListener("input", () => {
        mobileInput.value = mobileInput.value.replace(/\D/g, "").slice(0, 10);
    });
}

if (refreshCaptchaButton) {
    refreshCaptchaButton.addEventListener("click", generateCaptcha);
}

if (registerForm) {
    registerForm.addEventListener("submit", submitRegistration);
}

window.submitRegistration = submitRegistration;
window.generateCaptcha = generateCaptcha;

generateCaptcha();
