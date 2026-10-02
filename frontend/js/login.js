// ==========================================
// SafeRoad AI - Real Backend Login
// ==========================================

const API_BASE_URL = "http://localhost:8080/api";

const loginForm = document.getElementById("loginForm");

if (loginForm) {

    loginForm.addEventListener("submit", async function (event) {

        // Page reload prevent karo
        event.preventDefault();

        // ==========================================
        // Get Login Form Values
        // ==========================================

       const email = document.getElementById("loginEmail").value.trim();
       const password = document.getElementById("loginPassword").value;

        // ==========================================
        // Basic Validation
        // ==========================================

        if (!email) {
            alert("Please enter your email.");
            return;
        }

        if (!password) {
            alert("Please enter your password.");
            return;
        }

        // ==========================================
        // Login Request Data
        // ==========================================

        const loginData = {
            email: email,
            password: password
        };

        try {

            // ==========================================
            // Call Spring Boot Login API
            // ==========================================

            const response = await fetch(
                `${API_BASE_URL}/auth/login`,
                {
                    method: "POST",

                    headers: {
                        "Content-Type": "application/json"
                    },

                    body: JSON.stringify(loginData)
                }
            );

            const data = await response.json();

            // ==========================================
            // Login Failed
            // ==========================================

            if (!response.ok) {
                alert(data.message || "Invalid email or password.");
                return;
            }

            // ==========================================
            // Login Successful
            // ==========================================

            // JWT token browser session mein store karo
            sessionStorage.setItem("jwtToken", data.token);

            // User information bhi store karo
            sessionStorage.setItem(
                "safeRoadUser",
                JSON.stringify({
                    id: data.id,
                    fullName: data.fullName,
                    email: data.email,
                    mobile: data.mobile
                })
            );

            console.log("Login successful.");
            console.log("JWT token received.");

            alert("Login successful!");

            // Dashboard par redirect
            window.location.href = "../index.html";

        } catch (error) {

            console.error("Login Error:", error);

            alert(
                "Unable to connect to backend. " +
                "Please make sure Spring Boot is running."
            );
        }
    });
}