(() => {
    "use strict";

    const $ = id => document.getElementById(id);

    const popup = $("sensorPopup");
    const enableBtn = $("enableSensorFromHome");
    const notNowBtn = $("notNowSensor");
    const message = $("sensorReminderMessage");

    const badge = $("sensorMonitorBadge");
    const badgeLabel = $("sensorMonitorLabel");
    const toggleBtn = $("sensorToggleBtn");

    const alertBox = $("sensorDropAlert");
    const alertText = $("sensorDropAlertText");
    const silenceBtn = $("sensorSilenceBtn");

    if (
        !popup || !enableBtn || !notNowBtn || !message ||
        !badge || !badgeLabel || !toggleBtn ||
        !alertBox || !alertText || !silenceBtn
    ) {
        console.error(
            "Sensor Safety: Required HTML elements are missing. Check index.html."
        );
        return;
    }

    let enabled = false;
    let audioContext = null;

    let freeFallAt = 0;
    let freeFallConfirmed = false;
    let lastAlertAt = 0;
    let alertUntil = 0;

    let alertInterval = null;
    let countdownInterval = null;
    let reminderTimer = null;
    let reminderCount = 0;
    let pendingReminder = false;

    const ALERT_DURATION = 60_000;
    const REMINDER_DELAYS = [
        60_000,
        2 * 60_000,
        5 * 60_000,
        10 * 60_000
    ];

    function updateStatus(text, state = "off") {
        badgeLabel.textContent = text;
        badge.classList.toggle("is-on", state === "on");
        badge.classList.toggle("is-alert", state === "alert");

        toggleBtn.textContent = enabled ? "Disable" : "Enable";
    }

    function showPopup(isReminder = false) {
        if (enabled) return;

        if (isReminder) {
            message.textContent =
                `Reminder ${reminderCount} of ${REMINDER_DELAYS.length}: ` +
                "Enable Sensor Safety to monitor phone motion while this page is active.";
        } else {
            message.textContent = "";
        }

        popup.style.display = "flex";
    }

    function closePopup() {
        popup.style.display = "none";
    }

    function clearReminder() {
        if (reminderTimer !== null) {
            clearTimeout(reminderTimer);
            reminderTimer = null;
        }
        pendingReminder = false;
    }

    function scheduleReminder() {
        if (enabled || reminderCount >= REMINDER_DELAYS.length) return;

        clearReminder();

        const delay = REMINDER_DELAYS[reminderCount];

        reminderTimer = setTimeout(() => {
            reminderTimer = null;
            if (enabled) return;

            reminderCount++;

            if (document.visibilityState === "visible") {
                showPopup(true);
            } else {
                pendingReminder = true;
            }
        }, delay);
    }

    function formatNumber(value) {
        return Number.isFinite(value) ? value.toFixed(2) : "—";
    }

    function vibrate() {
        if ("vibrate" in navigator) {
            navigator.vibrate([180, 100, 180, 100, 240]);
        }
    }

    function playAlarmTone() {
        try {
            if (!audioContext) return;

            if (audioContext.state === "suspended") {
                audioContext.resume().catch(() => {});
            }

            const now = audioContext.currentTime;

            [0, 0.18, 0.36, 0.54].forEach((offset, index) => {
                const oscillator = audioContext.createOscillator();
                const gain = audioContext.createGain();

                oscillator.type = "square";
                oscillator.frequency.setValueAtTime(
                    index % 2 === 0 ? 1250 : 1650,
                    now + offset
                );

                gain.gain.setValueAtTime(0.0001, now + offset);
                gain.gain.exponentialRampToValueAtTime(
                    0.35,
                    now + offset + 0.018
                );
                gain.gain.setValueAtTime(0.35, now + offset + 0.10);
                gain.gain.exponentialRampToValueAtTime(
                    0.0001,
                    now + offset + 0.15
                );

                oscillator.connect(gain);
                gain.connect(audioContext.destination);

                oscillator.start(now + offset);
                oscillator.stop(now + offset + 0.16);
            });
        } catch (error) {
            console.warn("Could not play sensor alarm:", error);
        }
    }

    function stopAlarm() {
        alertUntil = 0;

        if (alertInterval !== null) {
            clearInterval(alertInterval);
            alertInterval = null;
        }

        if (countdownInterval !== null) {
            clearInterval(countdownInterval);
            countdownInterval = null;
        }

        if ("vibrate" in navigator) {
            navigator.vibrate(0);
        }

        alertBox.classList.remove("show");

        updateStatus(
            enabled ? "Sensor Safety: Monitoring" : "Sensor Safety: Off",
            enabled ? "on" : "off"
        );
    }

    function startAlarm(reason) {
        const now = Date.now();

        // Prevent repeated or overlapping alarms.
        if (now - lastAlertAt < 8000 || alertUntil > now) return;

        lastAlertAt = now;
        alertUntil = now + ALERT_DURATION;

        alertText.textContent =
            reason + ". Alarm will stop automatically within 60 seconds.";

        alertBox.classList.add("show");
        updateStatus("Sensor Safety: Possible drop detected", "alert");

        playAlarmTone();
        vibrate();

        alertInterval = setInterval(() => {
            if (Date.now() >= alertUntil) {
                stopAlarm();
                return;
            }

            playAlarmTone();
            vibrate();
        }, 900);

        countdownInterval = setInterval(() => {
            const secondsLeft = Math.max(
                0,
                Math.ceil((alertUntil - Date.now()) / 1000)
            );

            alertText.textContent =
                `${reason}. Alarm active: ${secondsLeft}s remaining.`;

            if (secondsLeft <= 0) stopAlarm();
        }, 250);
    }

    function onMotion(event) {
        if (!enabled) return;

        const acceleration =
            event.accelerationIncludingGravity || event.acceleration;

        if (!acceleration) return;

        const x = Number(acceleration.x) || 0;
        const y = Number(acceleration.y) || 0;
        const z = Number(acceleration.z) || 0;

        const magnitude = Math.sqrt(x * x + y * y + z * z);
        const now = Date.now();

        // A low-acceleration free-fall pattern must occur first.
        if (magnitude < 3.0) {
            if (!freeFallAt) {
                freeFallAt = now;
                freeFallConfirmed = false;
            }

            if (now - freeFallAt >= 100) {
                freeFallConfirmed = true;
            }

            return;
        }

        const freeFallDuration = freeFallAt ? now - freeFallAt : 0;

        // Alarm only when confirmed free-fall is followed by a sharp impact.
        if (
            freeFallConfirmed &&
            magnitude > 26 &&
            freeFallDuration >= 100 &&
            freeFallDuration <= 1200
        ) {
            startAlarm("Free-fall followed by a sharp impact");

            freeFallAt = 0;
            freeFallConfirmed = false;
            return;
        }

        // Reset stale sequences so ordinary motion/tilting does not trigger it.
        if (
            !freeFallAt ||
            freeFallDuration > 1200 ||
            (!freeFallConfirmed && magnitude > 8)
        ) {
            freeFallAt = 0;
            freeFallConfirmed = false;
        }
    }

    async function enableSensors() {
        if (enabled) return true;

        enableBtn.disabled = true;
        toggleBtn.disabled = true;
        message.textContent = "";

        try {
            // Create audio during the user's button interaction.
            const AudioContextClass =
                window.AudioContext || window.webkitAudioContext;

            if (AudioContextClass && !audioContext) {
                audioContext = new AudioContextClass();
            }

            if (
                audioContext &&
                audioContext.state === "suspended"
            ) {
                await audioContext.resume();
            }

            // Some iOS browser versions require explicit sensor permission.
            const permissionRequests = [];

            if (
                typeof DeviceMotionEvent !== "undefined" &&
                typeof DeviceMotionEvent.requestPermission === "function"
            ) {
                permissionRequests.push(
                    DeviceMotionEvent.requestPermission()
                );
            }

            if (
                typeof DeviceOrientationEvent !== "undefined" &&
                typeof DeviceOrientationEvent.requestPermission === "function"
            ) {
                permissionRequests.push(
                    DeviceOrientationEvent.requestPermission()
                );
            }

            const permissions = await Promise.all(permissionRequests);

            if (permissions.some(permission => permission !== "granted")) {
                throw new Error("Motion sensor permission was not granted.");
            }

            if (!("DeviceMotionEvent" in window)) {
                throw new Error(
                    "This browser does not support motion sensor events. " +
                    "Try a supported mobile browser over HTTPS."
                );
            }

            window.addEventListener("devicemotion", onMotion);

            enabled = true;
            freeFallAt = 0;
            freeFallConfirmed = false;

            clearReminder();
            closePopup();

            updateStatus("Sensor Safety: Monitoring", "on");

            return true;
        } catch (error) {
            console.error("Sensor Safety could not start:", error);

            message.textContent =
                error.message ||
                "Could not enable sensors. Check HTTPS and browser permissions.";

            return false;
        } finally {
            enableBtn.disabled = false;
            toggleBtn.disabled = false;
        }
    }

    function disableSensors() {
        enabled = false;

        window.removeEventListener("devicemotion", onMotion);

        clearReminder();
        stopAlarm();

        freeFallAt = 0;
        freeFallConfirmed = false;

        updateStatus("Sensor Safety: Off", "off");
    }

    // Enable sensor monitoring from the popup on the main dashboard.
    enableBtn.addEventListener("click", async () => {
        await enableSensors();
    });

    // Not Now hides the popup and schedules the next reminder.
    notNowBtn.addEventListener("click", () => {
        closePopup();
        scheduleReminder();
    });

    // The small dashboard control can reopen the prompt or disable monitoring.
    toggleBtn.addEventListener("click", () => {
        if (enabled) {
            disableSensors();
        } else {
            showPopup();
        }
    });

    silenceBtn.addEventListener("click", stopAlarm);

    document.addEventListener("visibilitychange", () => {
        if (
            document.visibilityState === "visible" &&
            pendingReminder &&
            !enabled
        ) {
            pendingReminder = false;
            showPopup(true);
        }
    });

    // Show the initial popup when the main page loads.
    updateStatus("Sensor Safety: Off", "off");
    showPopup();
})();