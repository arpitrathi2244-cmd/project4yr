/* =========================================================
   FILE: app.js
   PROJECT: AI Road Safety & Accident Prevention System

   PURPOSE:
   Controls frontend interactions.

   SECTIONS:
   01. UI element references
   02. Toast notifications
   03. Module navigation
   04. Emergency SOS
   05. Dashboard entrance animation
   06. Hover / pointer micro-interactions
   ========================================================= */

// ---------- 1. COLLECT MAIN UI ELEMENTS ----------
const sections = [...document.querySelectorAll(".page-section")];
const navItems = [...document.querySelectorAll(".nav-item")];
const toast = document.getElementById("toast");

/* ==================== 02. TOAST NOTIFICATIONS ==================== */
function showToast(message){
  toast.textContent = message;
  toast.classList.add("show");
  clearTimeout(window.__toastTimer);
  window.__toastTimer = setTimeout(()=>toast.classList.remove("show"), 2600);
}

/* ==================== 03. MODULE NAVIGATION ==================== */
function openSection(id){
  sections.forEach(s => s.classList.toggle("active", s.id === id));
  navItems.forEach(n => n.classList.toggle("active", n.dataset.section === id));
  window.scrollTo({top:0, behavior:"smooth"});
  if(id !== "dashboard") showToast(`${document.querySelector(`[data-section="${id}"]`)?.innerText.trim() || "Module"} opened`);
}

window.openSection = openSection;

navItems.forEach(item => item.addEventListener("click", () => openSection(item.dataset.section)));

document.getElementById("callNow")?.addEventListener("click", triggerSOS);
document.getElementById("notificationBtn")?.addEventListener("click", () => showToast("3 active safety notifications"));
document.getElementById("themeToggle")?.addEventListener("click", () => {
  document.body.classList.toggle("light-mode");
  showToast(document.body.classList.contains("light-mode") ? "Light mode preview" : "Dark mode enabled");
});

/* ==================== 04. EMERGENCY SOS ==================== */
function triggerSOS(){
  const ok = confirm("Emergency SOS will share your GPS location with the registered emergency contact. Continue?");
  if(ok) showToast("SOS triggered • GPS location sharing started");
}
window.triggerSOS = triggerSOS;

/* The top search belongs to live-map.js.
   Do not attach a second Enter/click handler here: it would conflict with
   destination autocomplete and route calculation. */
const globalSearch = document.getElementById("globalSearch");

document.addEventListener("keydown", e => {
  if((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k"){
    e.preventDefault();
    (document.getElementById("mapSearchInput") || globalSearch)?.focus();
  }
});

/* ==================== 05. DASHBOARD ENTRANCE ANIMATION ==================== */

document.querySelectorAll(".summary-card,.action-card,.panel").forEach((el,i)=>{
  el.style.animation = `fadeIn .45s ease ${Math.min(i*35,500)}ms both`;
});

// Premium cursor micro-interaction: add a tiny "bubble" class to clickable UI.
document.querySelectorAll("button, a, .summary-card, .action-card, .service-row, .module-list > div, .route-options > div").forEach(el=>{
  el.addEventListener("mouseenter",()=>el.classList.add("is-hovered"));
  el.addEventListener("mouseleave",()=>el.classList.remove("is-hovered"));
});

// Floating pointer glow follows the mouse on desktop.
const pointerGlow = document.createElement("div");
pointerGlow.className = "pointer-glow";
document.body.appendChild(pointerGlow);
document.addEventListener("mousemove", e=>{
  pointerGlow.style.left = `${e.clientX}px`;
  pointerGlow.style.top = `${e.clientY}px`;
});

/* ==================== 06. HOVER / POINTER MICRO-INTERACTION ==================== */
// We add a small class when the cursor enters/leaves interactive elements.
// The actual movement is controlled by CSS transitions in style.css.


// =========================================================
// 07. AUTHENTICATED USER PROFILE
// =========================================================

const API_BASE_URL = "http://localhost:8080/api";

/*
 * Logged-in user ka JWT sessionStorage se leta hai
 * aur backend se actual profile fetch karta hai.
 */
async function loadUserProfile() {

  const token = sessionStorage.getItem("jwtToken");

  // JWT nahi mila to user logged-in nahi hai
  if (!token) {
    console.warn("No JWT token found.");
    return;
  }

  try {

    const response = await fetch(
      `${API_BASE_URL}/user/profile`,
      {
        method: "GET",
        headers: {
          "Authorization": `Bearer ${token}`,
          "Content-Type": "application/json"
        }
      }
    );

    // Token invalid/expired
    if (response.status === 401 || response.status === 403) {
      console.warn("Authentication failed.");

      sessionStorage.removeItem("jwtToken");
      sessionStorage.removeItem("safeRoadUser");

      return;
    }

    if (!response.ok) {
      throw new Error("Profile request failed.");
    }

    const user = await response.json();

    // Latest user data browser session mein save karo
    sessionStorage.setItem(
      "safeRoadUser",
      JSON.stringify(user)
    );

    console.log("User profile loaded:", user);

      // Fill profile form with real backend data
const fullNameInput = document.getElementById("profileFullName");
const ageInput = document.getElementById("profileAge");
const mobileInput = document.getElementById("profileMobile");
const emailInput = document.getElementById("profileEmail");

if (fullNameInput) {
  fullNameInput.value = user.fullName || "";
}

if (ageInput) {
  ageInput.value = user.age || "";
}

if (mobileInput) {
  mobileInput.value = user.mobile || "";
}

if (emailInput) {
  emailInput.value = user.email || "";
}
    /*
     * Agar dashboard mein ye elements available hain,
     * to actual backend data display hoga.
     */
    document.querySelectorAll("[data-user-name]")
      .forEach(element => {
        element.textContent = user.fullName || "User";
      });

    document.querySelectorAll("[data-user-email]")
      .forEach(element => {
        element.textContent = user.email || "";
      });

  } catch (error) {

    console.error("Profile Error:", error);

    showToast("Unable to load user profile");
  }
}

// Function globally available
window.loadUserProfile = loadUserProfile;


// Page load hone par profile fetch karo
document.addEventListener("DOMContentLoaded", () => {
  loadUserProfile();
});

// =========================================================
// 08. UPDATE USER PROFILE
// =========================================================

const profileForm = document.getElementById("profileForm");

if (profileForm) {

  profileForm.addEventListener("submit", async function (event) {

    event.preventDefault();

    const token = sessionStorage.getItem("jwtToken");
    console.log("JWT exists:", !!token);


    if (!token) {
      showToast("Please login first.");
      return;
    }

    // Get profile form values
    const fullName =
      document.getElementById("profileFullName").value.trim();

    const age =
      Number(document.getElementById("profileAge").value);

    const mobile =
      document.getElementById("profileMobile").value.trim();

    const email =
      document.getElementById("profileEmail").value.trim();

    // Basic validation
    if (!fullName || !age || !mobile || !email) {
      showToast("Please fill all profile fields.");
      return;
    }

    if (!/^\d{10}$/.test(mobile)) {
      showToast("Mobile number must contain exactly 10 digits.");
      return;
    }

    const profileData = {
      fullName: fullName,
      age: age,
      mobile: mobile,
      email: email
    };

    try {

      const response = await fetch(
        `${API_BASE_URL}/user/profile`,
        {
          method: "PUT",

          headers: {
            "Authorization": `Bearer ${token}`,
            "Content-Type": "application/json"
          },

          body: JSON.stringify(profileData)
        }
      );

     const responseText = await response.text();

console.log("Profile Update Status:", response.status);
console.log("Profile Update Response:", responseText);

let data = {};

if (responseText) {
  try {
    data = JSON.parse(responseText);
  } catch (error) {
    console.error("Invalid JSON response:", responseText);
  }
}

if (!response.ok) {
  showToast(
    data.message ||
    `Profile update failed (${response.status})`
  );
  return;
}

      // Updated user data save karo
      sessionStorage.setItem(
        "safeRoadUser",
        JSON.stringify(data)
      );

      // Header name/email update karo
      document.querySelectorAll("[data-user-name]")
        .forEach(element => {
          element.textContent = data.fullName || "User";
        });

      document.querySelectorAll("[data-user-email]")
        .forEach(element => {
          element.textContent = data.email || "";
        });

      showToast("Profile updated successfully.");

    } catch (error) {

      console.error("Profile Update Error:", error);

      showToast(
        "Unable to connect to backend."
      );
    }
  });
}

// =========================================================
// 09. AUTHENTICATION UI
// =========================================================

// =========================================================
// 09. AUTHENTICATION UI
// =========================================================

function updateAuthenticationUI() {

    const token = sessionStorage.getItem("jwtToken");

    const authActions =
        document.getElementById("authActions");

    const userProfile =
        document.getElementById("userProfile");

    if (!authActions || !userProfile) {
        return;
    }

    // =========================================
    // LOGGED OUT
    // =========================================

    if (!token) {

        authActions.style.display = "flex";
        userProfile.style.display = "none";

        return;
    }

    // =========================================
    // LOGGED IN
    // =========================================

    authActions.style.display = "none";
    userProfile.style.display = "flex";

    // Get stored user
    const storedUser =
        sessionStorage.getItem("safeRoadUser");

    if (storedUser) {

        try {

            const user =
                JSON.parse(storedUser);

            // Name
            document
                .querySelectorAll("[data-user-name]")
                .forEach(element => {
                    element.textContent =
                        user.fullName || "User";
                });

            // Email
            document
                .querySelectorAll("[data-user-email]")
                .forEach(element => {
                    element.textContent =
                        user.email || "";
                });

            // =========================================
            // Generate Initials
            // =========================================

            const avatar =
                document.getElementById("userAvatar");

            if (avatar && user.fullName) {

                const parts =
                    user.fullName
                        .trim()
                        .split(/\s+/);

                let initials = "";

                if (parts.length >= 2) {

                    initials =
                        parts[0].charAt(0) +
                        parts[parts.length - 1].charAt(0);

                } else {

                    initials =
                        parts[0]
                            .substring(0, 2);
                }

                avatar.textContent =
                    initials.toUpperCase();
            }

        } catch (error) {

            console.error(
                "User data error:",
                error
            );
        }
    }
}


// Run authentication UI
document.addEventListener(
    "DOMContentLoaded",
    updateAuthenticationUI
);


// Run authentication check
document.addEventListener("DOMContentLoaded", () => {
  updateAuthenticationUI();
});

// =========================================================
// 10. FRONTEND LOGOUT
// =========================================================

document
    .getElementById("logoutBtn")
    ?.addEventListener("click", () => {

        // Remove JWT
        sessionStorage.removeItem("jwtToken");

        // Remove stored user data
        sessionStorage.removeItem("safeRoadUser");

        // Update header immediately
        updateAuthenticationUI();

        // Dashboard par hi raho
        showToast("Logged out successfully.");

    });