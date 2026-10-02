/*
 * SafeRoad - Database-backed live safety map + GPS geofence alerts
 *
 * IMPORTANT DESIGN:
 * 1. Map visibility is only a UI setting.
 * 2. Alerts are independent and continue even when a layer is hidden.
 * 3. Safety zones come from the Spring Boot database, not fake markers.
 * 4. OSM geometry saved by the backend is rendered as polygons/lines.
 */

// ===============================
// OSM ROAD NAMES
// ===============================

let roadNameLayer = L.layerGroup();

(() => {
    "use strict";

    const API_BASE = "http://localhost:8080/api/safety-zones";
    const DEFAULT_CENTER = [22.9734, 78.6569]; // India
    const DEFAULT_ZOOM = 5;

    const maps = new Map();
    const layerState = new Map();
    const layerVisibility = new Map();
    const zoneObjects = new Map();
    const loadedZoneRecords = new Map();

    let currentLocation = null;
    let gpsWatchId = null;
    let alertTimer = null;
    let alertRequestInFlight = false;
    let lastAlertByZone = new Map();
    let selectedLocationMarker = null;
    let searchDestination = null;
    let gpsAutoCentered = false;
    let gpsNameRequestInFlight = false;

    // Voice safety alerts: browser speech is unlocked after the user
    // interacts with the page once, then GPS geofence alerts can speak.
    let voiceAlertsEnabled = false;
    let voiceAlertInProgress = false;
    const liveZoneObjects = new Map();

    const CONFIGS = [
        {
            id: "safeRoadLiveMap",
            zoomIn: "dashboardZoomInBtn",
            zoomOut: "dashboardZoomOutBtn",
            locate: "dashboardLocateMeBtn",
            clearSelected: "dashboardClearSelectedBtn",
            direction: "dashboardDirectionBtn",
            toolbar: "dashboard"
        },
        {
            id: "roadSafetyMap",
            zoomIn: "zoomInBtn",
            zoomOut: "zoomOutBtn",
            locate: "locateMeBtn",
            clearSelected: "clearSelectedBtn",
            direction: "directionBtn",
            toolbar: "live"
        }
    ];

    function domMapKey(mapKey) {
        if (mapKey === "dashboard") return "safeRoadLiveMap";
        if (mapKey === "live") return "roadSafetyMap";
        return mapKey;
    }

    function statusMapKey(mapKey) {
        const domKey = domMapKey(mapKey);
        if (domKey === "safeRoadLiveMap") return "dashboard";
        if (domKey === "roadSafetyMap") return "live";
        return mapKey;
    }

    const TYPE_INFO = {
        SCHOOL: {
            label: "School",
            icon: "🏫",
            color: "#2563eb",
            fill: "#2563eb"
        },
        HILL_AREA: {
            label: "Hill Area",
            icon: "⛰️",
            color: "#d97706",
            fill: "#d97706"
        },
        WILDLIFE: {
            label: "Wildlife / Protected Area",
            icon: "🐘",
            color: "#7c3aed",
            fill: "#7c3aed"
        }
    };

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    function ensureAlertUi() {
        if (document.getElementById("safeRoadZoneAlert")) return;

        const style = document.createElement("style");
        style.id = "safeRoadZoneAlertStyle";
        style.textContent = `
            #safeRoadZoneAlert {
                position: fixed;
                left: 50%;
                top: 86px;
                transform: translateX(-50%) translateY(-15px);
                width: min(560px, calc(100vw - 28px));
                z-index: 100000;
                padding: 15px 18px;
                border: 1px solid rgba(255,255,255,.2);
                border-radius: 15px;
                background: rgba(5,24,43,.96);
                color: #fff;
                box-shadow: 0 18px 50px rgba(0,0,0,.35);
                backdrop-filter: blur(12px);
                opacity: 0;
                pointer-events: none;
                transition: .25s ease;
            }
            #safeRoadZoneAlert.show {
                opacity: 1;
                transform: translateX(-50%) translateY(0);
            }
            #safeRoadZoneAlert .alert-title {
                font-weight: 800;
                font-size: 16px;
                margin-bottom: 4px;
            }
            #safeRoadZoneAlert .alert-distance {
                font-size: 14px;
                opacity: .88;
            }
            #safeRoadZoneAlert button {
                float: right;
                border: 0;
                background: transparent;
                color: #fff;
                cursor: pointer;
                font-size: 18px;
            }
            .safe-road-click-marker {
                width: 22px;
                height: 22px;
                border-radius: 50%;
                border: 3px solid #fff;
                background: #1877ff;
                box-shadow: 0 0 0 5px rgba(24,119,255,.25);
            }
            .safe-road-zone-icon {
                width: 28px;
                height: 28px;
                display: grid;
                place-items: center;
                border-radius: 50%;
                background: rgba(5,24,43,.95);
                border: 2px solid currentColor;
                font-size: 15px;
                box-sizing: border-box;
            }
            .safe-road-selected {
                box-shadow: 0 0 0 5px rgba(24,119,255,.25);
            }
            [data-safe-road-voice="on"] .safe-road-direction-btn {
                box-shadow: 0 0 0 2px rgba(24,217,139,.45);
            }

            /* SafeRoad toolbar layout fix: keep every control in its own box. */
            [data-osm-toolbar],
            #dashboardMapFilters,
            #liveMapFilters {
                display: flex !important;
                align-items: center !important;
                justify-content: flex-start !important;
                flex-wrap: wrap !important;
                gap: 8px !important;
                width: 100% !important;
                box-sizing: border-box !important;
                overflow: visible !important;
            }

            [data-osm-toolbar] > *,
            #dashboardMapFilters > *,
            #liveMapFilters > * {
                position: static !important;
                inset: auto !important;
                float: none !important;
                transform: none !important;
                flex: 0 0 auto !important;
                box-sizing: border-box !important;
                margin: 0 !important;
            }

            [data-osm-toolbar] button,
            #dashboardMapFilters button,
            #liveMapFilters button {
                min-height: 48px !important;
                min-width: 84px !important;
                padding: 0 14px !important;
                white-space: nowrap !important;
                overflow: hidden !important;
                text-overflow: ellipsis !important;
                line-height: 1.15 !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: center !important;
            }

            [data-osm-toolbar] #dashboardDirectionBtn,
            [data-osm-toolbar] #directionBtn,
            #dashboardMapFilters #dashboardDirectionBtn,
            #liveMapFilters #directionBtn {
                min-width: 82px !important;
            }

            [data-osm-toolbar] #dashboardClearSelectedBtn,
            [data-osm-toolbar] #clearSelectedBtn,
            #dashboardMapFilters #dashboardClearSelectedBtn,
            #liveMapFilters #clearSelectedBtn {
                min-width: 88px !important;
            }

            @media (max-width: 700px) {
                [data-osm-toolbar] button,
                #dashboardMapFilters button,
                #liveMapFilters button {
                    min-width: 76px !important;
                    padding: 0 10px !important;
                    font-size: 13px !important;
                }

                [data-osm-toolbar] #dashboardDirectionBtn,
                [data-osm-toolbar] #directionBtn,
                #dashboardMapFilters #dashboardDirectionBtn,
                #liveMapFilters #directionBtn {
                    min-width: 78px !important;
                }

                [data-osm-toolbar] #dashboardClearSelectedBtn,
                [data-osm-toolbar] #clearSelectedBtn,
                #dashboardMapFilters #dashboardClearSelectedBtn,
                #liveMapFilters #clearSelectedBtn {
                    min-width: 84px !important;
                }
            }
        `;
        document.head.appendChild(style);

        const alert = document.createElement("div");
        alert.id = "safeRoadZoneAlert";
        alert.innerHTML = `
            <button type="button" id="safeRoadZoneAlertClose">×</button>
            <div class="alert-title" id="safeRoadZoneAlertTitle"></div>
            <div class="alert-distance" id="safeRoadZoneAlertDistance"></div>
        `;
        document.body.appendChild(alert);

        document.getElementById("safeRoadZoneAlertClose")
            .addEventListener("click", () => alert.classList.remove("show"));
    }

    function showAlert(zone, distanceKm, entered = false) {
        ensureAlertUi();

        const info = TYPE_INFO[zone.zoneType] || {
            label: zone.zoneType,
            icon: "⚠️"
        };

        const alert = document.getElementById("safeRoadZoneAlert");
        const title = document.getElementById("safeRoadZoneAlertTitle");
        const distance = document.getElementById("safeRoadZoneAlertDistance");

        title.textContent = entered
            ? `⚠️ You are entering ${info.label}`
            : `${info.icon} ${info.label} Ahead`;

        distance.textContent = entered
            ? `${zone.name || info.label} is at the current location.`
            : `${zone.name || info.label} is ${formatDistance(distanceKm)} ahead. Please drive carefully.`;

        alert.classList.add("show");

        if ("Notification" in window && Notification.permission === "granted") {
            new Notification(
                entered
                    ? `Entering ${info.label}`
                    : `${info.label} ahead`,
                {
                    body: entered
                        ? zone.name || info.label
                        : `${zone.name || info.label} is ${formatDistance(distanceKm)} away.`
                }
            );
        }

        speakSafetyAlert(zone, distanceKm, entered);
    }

    function speakSafetyAlert(zone, distanceKm, entered = false) {
        if (!voiceAlertsEnabled || !("speechSynthesis" in window)) return;

        const info = TYPE_INFO[zone.zoneType] || {
            label: zone.zoneType || "Safety zone",
            icon: "⚠️"
        };

        const name = String(zone.name || "").trim();
        const distanceText = formatDistance(distanceKm);

        let message;
        if (entered) {
            message = `Warning. You are entering ${info.label}${name ? `, ${name}` : ""}. Please slow down and drive carefully.`;
        } else {
            message = `Attention. ${info.label} ahead${name ? `, ${name}` : ""}. ${distanceText} ahead. Please drive carefully.`;
        }

        try {
            window.speechSynthesis.cancel();
            const utterance = new SpeechSynthesisUtterance(message);
            utterance.lang = "en-IN";
            utterance.rate = 0.92;
            utterance.pitch = 1.0;
            utterance.volume = 1.0;
            utterance.onstart = () => { voiceAlertInProgress = true; };
            utterance.onend = () => { voiceAlertInProgress = false; };
            utterance.onerror = () => { voiceAlertInProgress = false; };
            window.speechSynthesis.speak(utterance);
        } catch (error) {
            console.warn("Voice safety alert failed:", error);
        }
    }

    function unlockVoiceAlerts() {
        if (!voiceAlertsEnabled) {
            voiceAlertsEnabled = true;
            document.documentElement.dataset.safeRoadVoice = "on";
        }
    }

    function formatDistance(km) {
        if (km < 1) return `${Math.round(km * 1000)} m`;
        return `${km.toFixed(km < 10 ? 1 : 0)} km`;
    }

    function haversine(lat1, lon1, lat2, lon2) {
        const R = 6371000;
        const dLat = (lat2 - lat1) * Math.PI / 180;
        const dLon = (lon2 - lon1) * Math.PI / 180;

        const a =
            Math.sin(dLat / 2) ** 2 +
            Math.cos(lat1 * Math.PI / 180) *
            Math.cos(lat2 * Math.PI / 180) *
            Math.sin(dLon / 2) ** 2;

        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    function bearingBetween(lat1, lon1, lat2, lon2) {
        const p1 = lat1 * Math.PI / 180;
        const p2 = lat2 * Math.PI / 180;
        const dl = (lon2 - lon1) * Math.PI / 180;

        const y = Math.sin(dl) * Math.cos(p2);
        const x = Math.cos(p1) * Math.sin(p2)
            - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);

        return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
    }

    function angularDifference(a, b) {
        const diff = Math.abs(a - b) % 360;
        return diff > 180 ? 360 - diff : diff;
    }

    function isZoneAhead(zone, position) {
        if (position.heading == null || Number.isNaN(position.heading)) {
            return true;
        }

        const bearing = bearingBetween(
            position.lat,
            position.lng,
            zone.latitude,
            zone.longitude
        );

        // 75° corridor in front of the vehicle.
        return angularDifference(position.heading, bearing) <= 75;
    }

    function createZoneIcon(type) {
        const info = TYPE_INFO[type] || { icon: "⚠️", color: "#1877ff" };

        return L.divIcon({
            className: "",
            html: `
                <div
                    class="safe-road-zone-icon"
                    style="color:${info.color};border-color:${info.color}"
                >
                    ${info.icon}
                </div>
            `,
            iconSize: [28, 28],
            iconAnchor: [14, 14],
            popupAnchor: [0, -14]
        });
    }

    function popupHtml(zone, distanceMeters = null) {
        const info = TYPE_INFO[zone.zoneType] || {
            label: zone.zoneType,
            icon: "⚠️"
        };

        let html = `
            <div style="min-width:210px">
                <strong>${info.icon} ${escapeHtml(zone.name)}</strong>
                <div style="margin-top:5px">
                    ${escapeHtml(info.label)}
                </div>
        `;

        if (distanceMeters != null) {
            html += `<div style="margin-top:5px">Distance: ${formatDistance(distanceMeters / 1000)}</div>`;
        }

        if (zone.speedLimit) {
            html += `<div>Recommended speed: ${zone.speedLimit} km/h</div>`;
        }

        if (zone.description) {
            html += `<div style="margin-top:5px">${escapeHtml(zone.description)}</div>`;
        }

        if (zone.source === "OPENSTREETMAP"
                && zone.sourceType
                && zone.sourceId) {
            html += `
                <div style="margin-top:7px">
                    <a
                        href="https://www.openstreetmap.org/${encodeURIComponent(zone.sourceType)}/${zone.sourceId}"
                        target="_blank"
                        rel="noopener"
                    >Open in OpenStreetMap</a>
                </div>
            `;
        }

        html += "</div>";
        return html;
    }

    function geometryToLatLngs(zone) {
        if (!zone.geometryJson) return null;

        try {
            const points = JSON.parse(zone.geometryJson);
            if (!Array.isArray(points)) return null;

            const result = points
                .filter(p => Array.isArray(p) && p.length >= 2)
                .map(p => [Number(p[0]), Number(p[1])])
                .filter(p => Number.isFinite(p[0]) && Number.isFinite(p[1]));

            return result.length >= 2 ? result : null;
        } catch {
            return null;
        }
    }

    function renderZoneOnMap(map, zone, typeLayer) {
        const zoneKey = `${map._leaflet_id}:${zone.id ?? `${zone.sourceType || "osm"}:${zone.sourceId || `${zone.latitude}:${zone.longitude}`}`}`;
        const existing = zoneObjects.get(zoneKey);
        if (existing) {
            // A route recalculation may clear a Leaflet layer without deleting
            // the cached object. Re-attach it when the user turns the layer on.
            if (typeLayer && !typeLayer.hasLayer(existing)) {
                existing.addTo(typeLayer);
            }
            return;
        }

        const info = TYPE_INFO[zone.zoneType] || TYPE_INFO.SCHOOL;
        const geometry = geometryToLatLngs(zone);
        let object;

        if (geometry && zone.zoneType === "WILDLIFE" && geometry.length >= 3) {
            object = L.polygon(geometry, {
                color: info.color,
                weight: 2,
                fillColor: info.fill,
                fillOpacity: 0.18
            });
        } else if (geometry && zone.zoneType === "HILL_AREA"
                && geometry.length >= 2) {
            object = L.polyline(geometry, {
                color: info.color,
                weight: 4,
                opacity: 0.85,
                dashArray: "7 5"
            });
        } else {
            object = L.marker(
                [zone.latitude, zone.longitude],
                { icon: createZoneIcon(zone.zoneType) }
            );
        }

        object.bindPopup(popupHtml(zone));
        object.addTo(typeLayer);

        zoneObjects.set(zoneKey, object);
    }

    function clearMapZones(mapKey) {
        const state = layerState.get(mapKey);
        if (!state) return;

        Object.values(state).forEach(layer => layer.clearLayers());

        for (const key of [...zoneObjects.keys()]) {
            if (key.startsWith(`${maps.get(mapKey)?._leaflet_id}:`)) {
                zoneObjects.delete(key);
            }
        }
    }

    function getVisibility(mapKey) {
        if (!layerVisibility.has(mapKey)) {
            // School, Hill and Wildlife layers start OFF.
            // Their data is still loaded for route-safety calculation and alerts.
            layerVisibility.set(mapKey, {
                schools: false,
                hills: false,
                wildlife: false
            });
        }
        return layerVisibility.get(mapKey);
    }

    function applyVisibility(mapKey) {
        const map = maps.get(mapKey);
        const state = layerState.get(mapKey);
        const visibility = getVisibility(mapKey);

        if (!map || !state) return;

        Object.entries({
            schools: state.schools,
            hills: state.hills,
            wildlife: state.wildlife
        }).forEach(([key, layer]) => {
            if (visibility[key]) {
                if (!map.hasLayer(layer)) layer.addTo(map);
            } else if (map.hasLayer(layer)) {
                map.removeLayer(layer);
            }
        });
    }

    function zoneButtonKey(button) {
        if (!button) return null;

        const raw = String(
            button.dataset.zone ||
            button.dataset.type ||
            button.getAttribute("aria-label") ||
            button.getAttribute("title") ||
            button.id ||
            button.textContent ||
            ""
        ).trim().toLowerCase();

        if (/\bschool(s)?\b/.test(raw)) return "schools";
        if (/\bhill(s)?\b|hill_area/.test(raw)) return "hills";
        if (/\bwildlife\b|protected[ _-]?area|nature[ _-]?reserve/.test(raw)) return "wildlife";
        return null;
    }

    function zoneTypeForLayerKey(key) {
        if (key === "schools") return "SCHOOL";
        if (key === "hills") return "HILL_AREA";
        if (key === "wildlife") return "WILDLIFE";
        return null;
    }

    function routeMapKey() {
        if (window.safeRoadGeneratedRoutesMapKey) {
            return domMapKey(window.safeRoadGeneratedRoutesMapKey);
        }
        if (maps.has("roadSafetyMap")) return "roadSafetyMap";
        if (maps.has("safeRoadLiveMap")) return "safeRoadLiveMap";
        return null;
    }

    function populateRouteLayerFromGeneratedRoutes(mapKey, layerKey) {
        const map = maps.get(mapKey);
        const state = layerState.get(mapKey);
        const layer = state?.[layerKey];
        if (!map || !state || !layer) return 0;

        const wantedType = zoneTypeForLayerKey(layerKey);
        if (!wantedType) return 0;

        const routes = Array.isArray(window.generatedRoutes) ? window.generatedRoutes : [];
        const seen = new Set();
        let count = 0;

        for (const route of routes) {
            for (const zone of (route.routeZones || [])) {
                if (String(zone.zoneType || "").toUpperCase() !== wantedType) continue;
                const id = String(
                    zone.id ??
                    `${zone.zoneType}:${zone.latitude}:${zone.longitude}`
                );
                if (seen.has(id)) continue;
                seen.add(id);
                renderZoneOnMap(map, zone, layer);
                count++;
            }
        }

        return count;
    }

    function toggleRouteZoneLayer(mapKey, layerKey, button = null) {
        const map = maps.get(mapKey);
        const state = layerState.get(mapKey);
        if (!map || !state || !state[layerKey]) return;

        const visibility = getVisibility(mapKey);
        visibility[layerKey] = !visibility[layerKey];

        if (visibility[layerKey]) {
            const count = populateRouteLayerFromGeneratedRoutes(mapKey, layerKey);
            const layer = state[layerKey];
            if (count > 0) {
                layer.addTo(map);
            }

            const label = layerKey === "schools" ? "Schools"
                : layerKey === "hills" ? "Hills"
                : "Wildlife";

            setStatus(
                statusMapKey(mapKey),
                count
                    ? `${label}: ${count} nearby route locations shown.`
                    : `${label}: no nearby route locations found.`
            );
        } else {
            map.removeLayer(state[layerKey]);
            setStatus(
                statusMapKey(mapKey),
                `${layerKey === "schools" ? "Schools" : layerKey === "hills" ? "Hills" : "Wildlife"} hidden`
            );
        }

        if (button) {
            button.classList.toggle("active", visibility[layerKey]);
            button.setAttribute("aria-pressed", visibility[layerKey] ? "true" : "false");
        }
    }

    function bindZoneButton(button, defaultMapKey = null) {
        if (!button || button.dataset.safeRoadZoneBound === "true") return;
        const key = zoneButtonKey(button);
        if (!key) return;

        button.dataset.safeRoadZoneBound = "true";
        button.dataset.safeRoadLayerKey = key;
        button.setAttribute("aria-pressed", getVisibility(domMapKey(defaultMapKey || routeMapKey() || "roadSafetyMap"))[key] ? "true" : "false");

        button.addEventListener("click", event => {
            event.preventDefault();
            event.stopPropagation();

            const mapKey = domMapKey(defaultMapKey || routeMapKey());
            if (!mapKey || !window.generatedRoutes?.length) {
                setStatus(statusMapKey(mapKey || "roadSafetyMap"), "First calculate a route.");
                return;
            }

            toggleRouteZoneLayer(mapKey, key, button);
        });
    }

    function bindGlobalZoneButtons() {
        document.querySelectorAll("button, [role=button]").forEach(button => {
            const key = zoneButtonKey(button);
            if (!key) return;
            if (button.closest("[data-osm-toolbar], #dashboardMapFilters, #liveMapFilters")) return;
            bindZoneButton(button);
        });
    }

    function setupToolbar(mapKey, toolbarKey) {
        const toolbar = document.querySelector(
            `[data-osm-toolbar="${toolbarKey}"], #${toolbarKey}MapFilters`
        );

        if (!toolbar || toolbar.dataset.bound === "true") return;
        toolbar.dataset.bound = "true";

        const visibility = getVisibility(mapKey);

        // Support old data attributes, IDs, aria labels and visible button text.
        toolbar.querySelectorAll("button, [role=button]").forEach(button => {
            const raw = String(
                button.dataset.zone ||
                button.dataset.type ||
                button.getAttribute("aria-label") ||
                button.getAttribute("title") ||
                button.id ||
                button.textContent ||
                ""
            ).trim();
            const key = zoneButtonKey(button);

            if (!key) {
                if (raw === "steepRoads") button.style.display = "none";
                return;
            }

            // Initial state is OFF. Keep button UI synchronized with the layer.
            button.classList.toggle("active", Boolean(visibility[key]));
            button.setAttribute("aria-pressed", visibility[key] ? "true" : "false");
            button.dataset.safeRoadLayerKey = key;

            button.addEventListener("click", event => {
                event.preventDefault();
                event.stopPropagation();

                const activeMapKey = routeMapKey() || mapKey;
                if (!window.generatedRoutes?.length) {
                    setStatus(activeMapKey, "First select a destination and calculate a route.");
                    return;
                }

                toggleRouteZoneLayer(activeMapKey, key, button);
            });
        });
    }

    function typeLayerFor(state, type) {
        if (type === "SCHOOL") return state.schools;
        if (type === "HILL_AREA") return state.hills;
        if (type === "WILDLIFE") return state.wildlife;
        return null;
    }

    async function loadAllTypeZones(mapKey, filterType) {
        const map = maps.get(mapKey);
        const state = layerState.get(mapKey);
        if (!map || !state) return;

        const typeMap = {
            schools: "SCHOOL",
            hills: "HILL_AREA",
            wildlife: "WILDLIFE"
        };
        const apiType = typeMap[filterType];
        if (!apiType) return;

        const layer = typeLayerFor(state, apiType);
        if (!layer) return;

        try {
            setStatus(mapKey, `Loading all saved ${apiType.replace("_", " ").toLowerCase()} zones...`);

            const response = await fetch(
                `${API_BASE}/type/${encodeURIComponent(apiType)}`
            );

            if (!response.ok) {
                throw new Error(`Zone API returned ${response.status}`);
            }

            const zones = await response.json();
            const existingRecords = loadedZoneRecords.get(mapKey) || [];
            const otherTypes = existingRecords.filter(zone => zone.zoneType !== apiType);
            loadedZoneRecords.set(mapKey, [...otherTypes, ...(Array.isArray(zones) ? zones : [])]);
            layer.clearLayers();

            zones.forEach(zone => renderZoneOnMap(map, zone, layer));
            applyVisibility(mapKey);

            setStatus(
                mapKey,
                `${zones.length} saved ${apiType.replace("_", " ").toLowerCase()} zones loaded`
            );
        } catch (error) {
            console.error("All-zone load failed:", error);
            setStatus(mapKey, "Could not load saved zones.");
        }
    }

    async function loadLiveOsmZones(mapKey) {
        const map = maps.get(mapKey);
        const state = layerState.get(mapKey);
        if (!map || !state || map.getZoom() < 9) return;

        const bounds = map.getBounds();
        if ((bounds.getNorth() - bounds.getSouth()) > 2.5 ||
            (bounds.getEast() - bounds.getWest()) > 2.5) return;

        const visibleTypes = [
            ["SCHOOL", state.schools],
            ["HILL_AREA", state.hills],
            ["WILDLIFE", state.wildlife]
        ];

        for (const [type, layer] of visibleTypes) {
            const key = `${mapKey}:${type}`;
            if (!getVisibility(mapKey)[type === "SCHOOL" ? "schools" : type === "HILL_AREA" ? "hills" : "wildlife"]) continue;

            try {
                const params = new URLSearchParams({
                    south: bounds.getSouth(),
                    west: bounds.getWest(),
                    north: bounds.getNorth(),
                    east: bounds.getEast(),
                    zoneType: type
                });

                const response = await fetch(`${API_BASE}/live?${params.toString()}`);
                if (!response.ok) continue;
                const zones = await response.json();

                for (const oldKey of [...liveZoneObjects.keys()]) {
                    if (oldKey.startsWith(key + ":")) {
                        const obj = liveZoneObjects.get(oldKey);
                        if (obj && layer.hasLayer(obj)) layer.removeLayer(obj);
                        for (const [objectKey, objectValue] of zoneObjects.entries()) {
                            if (objectValue === obj) zoneObjects.delete(objectKey);
                        }
                        liveZoneObjects.delete(oldKey);
                    }
                }

                const records = loadedZoneRecords.get(mapKey) || [];
                const recordKeys = new Set(records.map(zone => String(zone.id ?? `${zone.zoneType}:${zone.latitude}:${zone.longitude}`)));
                zones.forEach(zone => {
                    const recordKey = String(zone.id ?? `${zone.zoneType}:${zone.latitude}:${zone.longitude}`);
                    if (!recordKeys.has(recordKey)) records.push(zone);
                    const before = zoneObjects.size;
                    renderZoneOnMap(map, zone, layer);
                    // renderZoneOnMap stores the object under the normal key.
                    // Track live keys so they can be refreshed without touching DB data.
                    const liveKey = `${key}:${zone.sourceType || "osm"}:${zone.sourceId || `${zone.latitude}:${zone.longitude}`}`;
                    const objectKey = `${map._leaflet_id}:${zone.id ?? `${zone.sourceType || "osm"}:${zone.sourceId || `${zone.latitude}:${zone.longitude}`}`}`;
                    const object = zoneObjects.get(objectKey);
                    if (object) liveZoneObjects.set(liveKey, object);
                });

                loadedZoneRecords.set(mapKey, records);

                if (zones.length) {
                    setStatus(mapKey, `Live OSM: ${zones.length} ${type.replace("_", " ").toLowerCase()} features visible`);
                }
            } catch (error) {
                console.warn(`Live OSM ${type} failed:`, error);
            }
        }
    }

    async function loadDatabaseZones(mapKey) {
        const map = maps.get(mapKey);
        const state = layerState.get(mapKey);
        if (!map || !state) return;

        // IMPORTANT: Do NOT load schools/hills/wildlife from the current
        // map viewport. These zones are loaded only after Current Location
        // + Destination produces routes. This keeps the safety data
        // route-specific instead of showing zones everywhere on the map.
        if (Array.isArray(window.generatedRoutes) && window.generatedRoutes.length) {
            applyVisibility(mapKey);
            return;
        }

        loadedZoneRecords.set(mapKey, []);
        clearMapZones(mapKey);
        applyVisibility(mapKey);
        setStatus(mapKey, "Map ready — safety zones will load for the selected route.");
    }

    function routeZoneDistanceMeters(routeSamples, zone) {
        if (!Array.isArray(routeSamples) || !routeSamples.length) return Infinity;
        const lat = Number(zone?.latitude);
        const lng = Number(zone?.longitude);
        if (!Number.isFinite(lat) || !Number.isFinite(lng)) return Infinity;
        let nearest = Infinity;
        for (const [sampleLat, sampleLng] of routeSamples) {
            nearest = Math.min(nearest, haversine(sampleLat, sampleLng, lat, lng));
            if (nearest <= 1500) return nearest;
        }
        return nearest;
    }

    function buildRouteZoneBboxes(routeModels, bufferMeters = 1800) {
        const points = [];
        for (const model of routeModels || []) {
            const coords = model?.raw?.geometry?.coordinates;
            if (!Array.isArray(coords)) continue;
            for (const point of coords) {
                if (Array.isArray(point) && point.length >= 2) {
                    points.push([Number(point[1]), Number(point[0])]);
                }
            }
        }
        if (!points.length) return [];

        // Route loading uses the local PostGIS database; this helper remains available for other callers.
        const bufferLat = bufferMeters / 111000;
        const boxes = [];
        let chunk = [];

        const flush = () => {
            if (!chunk.length) return;
            let south = Infinity, west = Infinity, north = -Infinity, east = -Infinity;
            chunk.forEach(([lat, lng]) => {
                south = Math.min(south, lat);
                north = Math.max(north, lat);
                west = Math.min(west, lng);
                east = Math.max(east, lng);
            });
            const midLat = (south + north) / 2;
            const bufferLng = bufferMeters / Math.max(111000 * Math.cos(midLat * Math.PI / 180), 30000);
            boxes.push({
                south: Math.max(-90, south - bufferLat),
                west: Math.max(-180, west - bufferLng),
                north: Math.min(90, north + bufferLat),
                east: Math.min(180, east + bufferLng)
            });
            chunk = [];
        };

        for (const point of points) {
            if (!chunk.length) {
                chunk.push(point);
                continue;
            }
            const test = [...chunk, point];
            const lats = test.map(x => x[0]);
            const lngs = test.map(x => x[1]);
            if ((Math.max(...lats) - Math.min(...lats)) > 1.8 ||
                (Math.max(...lngs) - Math.min(...lngs)) > 1.8) {
                flush();
            }
            chunk.push(point);
        }
        flush();

        // Deduplicate overlapping boxes.
        const seen = new Set();
        return boxes.filter(b => {
            const key = [b.south, b.west, b.north, b.east].map(v => v.toFixed(4)).join(',');
            if (seen.has(key)) return false;
            seen.add(key);
            return true;
        });
    }
        /*
     SafeRoad — PER-ROUTE GEOFENCE FIX
     Replace the existing loadRouteSafetyZones() and calculateRouteSafety()
     functions in map.js with the versions below.

     What this fixes:
     - Each route gets its own School/Hill/Wildlife zone list.
     - Route A zones are NOT counted for Route B/C.
     - API requests are made once per route/corridor, not on every GPS update.
     - Existing route UI fields nearbySchools / nearbyHills / nearbyWildlife remain compatible.
    */

    async function loadRouteSafetyZones(routeModels, mapKey) {
        const resolvedMapKey = domMapKey(mapKey);
        const map = maps.get(resolvedMapKey);
        const state = layerState.get(resolvedMapKey);
        if (!map || !state || !Array.isArray(routeModels) || !routeModels.length) return;

        routeModels.forEach(model => {
            model.routeZones = [];
            model.nearbySchools = 0;
            model.nearbyHills = 0;
            model.nearbyWildlife = 0;
        });

        const visualRecords = new Map();
        setStatus(
            statusMapKey(mapKey),
            `Checking local OSM safety zones for ${routeModels.length} routes...`
        );

        for (const model of routeModels) {
            const routeSamples = routePointSamples(model.raw, 160);
            if (routeSamples.length < 2) continue;

            try {
                const response = await fetch(`${API_BASE}/local-route`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        coordinates: routeSamples,
                        radiusMeters: 1200
                    })
                });

                if (!response.ok) {
                    const message = await response.text().catch(() => "");
                    throw new Error(`HTTP ${response.status} ${message}`.trim());
                }

                const zones = await response.json();
                if (!Array.isArray(zones)) continue;

                const routeRecords = new Map();
                for (const zone of zones) {
                    const id = String(
                        zone.id ??
                        `${zone.zoneType}:${zone.latitude}:${zone.longitude}`
                    );
                    routeRecords.set(id, zone);
                    visualRecords.set(id, zone);

                    if (zone.zoneType === "SCHOOL") model.nearbySchools++;
                    else if (zone.zoneType === "HILL_AREA") model.nearbyHills++;
                    else if (zone.zoneType === "WILDLIFE") model.nearbyWildlife++;
                }

                model.routeZones = [...routeRecords.values()];
            } catch (error) {
                console.error(`Local OSM route zones failed for Route ${model.index}:`, error);
                setStatus(
                    statusMapKey(mapKey),
                    `Local OSM safety-zone check failed for Route ${model.index + 1}.`
                );
            }
        }

        loadedZoneRecords.set(resolvedMapKey, [...visualRecords.values()]);

        state.schools.clearLayers();
        state.hills.clearLayers();
        state.wildlife.clearLayers();

        for (const zone of visualRecords.values()) {
            const layer = typeLayerFor(state, zone.zoneType);
            if (layer) renderZoneOnMap(map, zone, layer);
        }

        applyVisibility(resolvedMapKey);

        const total = routeModels.reduce(
            (sum, route) => sum + (route.routeZones?.length || 0),
            0
        );

        setStatus(
            statusMapKey(mapKey),
            `${total} local OSM safety locations found across ${routeModels.length} routes.`
        );
    }

    function calculateRouteSafety(route, shortestDistanceKm, mapKey, routeModel = null) {
        // Geofencing is an alert subsystem only. School/Hill/Wildlife counts
        // are kept for display/alerts and are deliberately NOT used to rank routes.
        const zones = Array.isArray(routeModel?.routeZones)
            ? routeModel.routeZones
            : [];

        let nearbySchools = 0;
        let nearbyHills = 0;
        let nearbyWildlife = 0;
        const counted = new Set();

        for (const zone of zones) {
            const id = String(
                zone.id ?? `${zone.zoneType}:${zone.latitude}:${zone.longitude}`
            );
            if (counted.has(id)) continue;
            counted.add(id);

            if (zone.zoneType === "SCHOOL") nearbySchools++;
            else if (zone.zoneType === "HILL_AREA") nearbyHills++;
            else if (zone.zoneType === "WILDLIFE") nearbyWildlife++;
        }

        const distanceKm = Number(route?.distance || 0) / 1000;
        const distancePenalty = shortestDistanceKm > 0
            ? Math.min(12, Math.max(0, ((distanceKm / shortestDistanceKm) - 1) * 20))
            : 0;

        // This is only a temporary pre-AI display score. Final route ranking
        // comes from live weather + traffic + historical accident AI.
        const fallbackScore = Math.max(0, Math.min(100, Math.round(100 - distancePenalty)));

        return {
            safetyScore: fallbackScore,
            risk: "ANALYZING",
            nearbySchools,
            nearbyHills,
            nearbyWildlife,
            distanceKm,
            durationMin: Number(route?.duration || 0) / 60,
            source: "Awaiting live weather + traffic + AI analysis"
        };
    }

    function setStatus(key, message) {
        const el = document.querySelector(
            `[data-osm-status="${key}"]`
        );
        if (el) el.textContent = message;
    }

    function removeSearchDestination(map) {
        if (!map) return;

        // Search destination marker
        if (map._safeRoadSearchMarker && map.hasLayer(map._safeRoadSearchMarker)) {
            map.removeLayer(map._safeRoadSearchMarker);
        }
        map._safeRoadSearchMarker = null;

        if (map._safeRoadRouteLine && map.hasLayer(map._safeRoadRouteLine)) {
            map.removeLayer(map._safeRoadRouteLine);
        }
        map._safeRoadRouteLine = null;
        removeRouteLayersOnly(map);

        const removeTaggedRouteLayers = [];
        map.eachLayer(layer => {
            if (layer && layer._safeRoadRouteLayer === true) removeTaggedRouteLayers.push(layer);
        });
        removeTaggedRouteLayers.forEach(layer => {
            if (map.hasLayer(layer)) map.removeLayer(layer);
        });
    }

    function clearSelectedLocation() {
        // IMPORTANT:
        // This is the actual handler used by both Clear buttons.
        // It must clear the destination AND every direction route.
        maps.forEach(map => {
            map._safeRoadDirectionRunId = (map._safeRoadDirectionRunId || 0) + 1;
            const marker = map._safeRoadSelectedMarker;
            if (marker && map.hasLayer(marker)) {
                map.removeLayer(marker);
            }
            map._safeRoadSelectedMarker = null;

            removeSearchDestination(map);
        });

        selectedLocationMarker = null;
        searchDestination = null;

        const name = document.getElementById("selectedLocationName");
        const coords = document.getElementById("selectedLocationCoords");
        if (name) name.textContent = "No location selected";
        if (coords) coords.textContent = "Click anywhere on the map";

        const input = document.getElementById("mapSearchInput");
        const results = document.getElementById("mapSearchResults");
        if (input) input.value = "";
        if (results) {
            results.innerHTML = "";
            results.style.display = "none";
        }

        // Clear route summary UI if the page has these elements.
        [
            "routeDistance",
            "routeDuration",
            "directionDistance",
            "directionDuration",
            "routeSummary"
        ].forEach(id => {
            const el = document.getElementById(id);
            if (el) {
                if (id === "routeSummary") {
                    el.innerHTML = "";
                } else {
                    el.textContent = "—";
                }
            }
        });

        window.generatedRoutes = [];
        window.safeRoadRouteData = [];
        window.safeRoadRecommendedRoute = null;
        window.safeRoadGeneratedRoutesMapKey = null;
        lastAlertByZone.clear();
        localStorage.removeItem("saferoad.routeCandidates");
        localStorage.removeItem("saferoad.routeUpdatedAt");
        document.querySelectorAll("#safe-route .route-options, .route-options[data-safe-road-routes]").forEach(container => {
            container.innerHTML = `<div class="safe-road-route-card"><b>No route selected</b><span>Search a destination, then click Direction.</span></div>`;
        });

        maps.forEach(map => {
            setStatus(
                map._safeRoadMapKey || "dashboard",
                "Selection and directions cleared."
            );
        });
    }


    async function reverseGeocode(lat, lng) {
        try {
            const url = "https://nominatim.openstreetmap.org/reverse?" +
                new URLSearchParams({
                    lat: String(lat),
                    lon: String(lng),
                    format: "jsonv2",
                    zoom: "18",
                    addressdetails: "1"
                });

            const response = await fetch(url, {
                headers: { "Accept": "application/json" }
            });
            if (!response.ok) throw new Error(`Reverse geocoder ${response.status}`);
            const data = await response.json();
            return data.display_name || "Selected location";
        } catch (error) {
            console.warn("Reverse geocoding failed:", error);
            return "Location name unavailable";
        }
    }

    function setSearchDestination(map, lat, lng, name) {
        searchDestination = { lat, lng, name };

        // A new destination invalidates previous routes and alert context.
        maps.forEach(otherMap => {
            otherMap._safeRoadDirectionRunId = (otherMap._safeRoadDirectionRunId || 0) + 1;
            removeRouteLayersOnly(otherMap);
        });
        window.generatedRoutes = [];
        window.safeRoadRouteData = [];
        window.safeRoadRecommendedRoute = null;
        window.safeRoadGeneratedRoutesMapKey = null;
        lastAlertByZone.clear();

        maps.forEach(otherMap => {
            removeSearchDestination(otherMap);
            const destinationMarker = L.marker([lat, lng], {
                icon: L.divIcon({
                    className: "",
                    html: '<div class="safe-road-destination-marker">●</div>',
                    iconSize: [24, 24],
                    iconAnchor: [12, 12]
                })
            }).addTo(otherMap);

            destinationMarker.bindPopup(`
                <div style="min-width:230px">
                    <strong>📍 ${escapeHtml(name || "Searched location")}</strong>
                    <div style="margin-top:5px;font-size:12px">${escapeHtml(name || "")}</div>
                    <div style="margin-top:6px">Latitude: ${lat.toFixed(6)}</div>
                    <div>Longitude: ${lng.toFixed(6)}</div>
                </div>
            `);
            otherMap._safeRoadSearchMarker = destinationMarker;
        });

        map.setView([lat, lng], Math.max(map.getZoom(), 14));
        if (map._safeRoadSearchMarker) map._safeRoadSearchMarker.openPopup();

        const nameEl = document.getElementById("selectedLocationName");
        const coordsEl = document.getElementById("selectedLocationCoords");
        if (nameEl) nameEl.textContent = name || "Searched location";
        if (coordsEl) coordsEl.textContent = `${lat.toFixed(6)}, ${lng.toFixed(6)}`;
        setStatus(map._safeRoadMapKey || "dashboard", `Selected: ${name || "location"}. Click Direction to route from your GPS location.`);
    }

    function routeSignature(route) {
        const coords = Array.isArray(route?.geometry?.coordinates)
            ? route.geometry.coordinates
            : [];
        if (coords.length < 2) return "";

        const indexes = [
            0,
            Math.floor(coords.length * 0.20),
            Math.floor(coords.length * 0.40),
            Math.floor(coords.length * 0.60),
            Math.floor(coords.length * 0.80),
            coords.length - 1
        ];

        return indexes.map(i => {
            const point = coords[i] || [0, 0];
            return `${Number(point[0]).toFixed(4)},${Number(point[1]).toFixed(4)}`;
        }).join("|");
    }

    function deduplicateRoutes(routes) {
        const seen = new Set();
        const unique = [];

        for (const route of routes) {
            const signature = routeSignature(route);
            if (!signature || seen.has(signature)) continue;
            seen.add(signature);
            unique.push(route);
        }
        return unique;
    }

    function buildCorridorWaypoints(startLat, startLng, endLat, endLng) {
        const dLat = endLat - startLat;
        const dLng = endLng - startLng;
        const length = Math.sqrt((dLat * dLat) + (dLng * dLng)) || 1;
        const perpLat = -dLng / length;
        const perpLng = dLat / length;

        const offsets = [0.015, -0.015, 0.035, -0.035];
        const fractions = [0.35, 0.50, 0.65];
        const result = [];

        for (const fraction of fractions) {
            const baseLat = startLat + dLat * fraction;
            const baseLng = startLng + dLng * fraction;
            for (const offset of offsets) {
                result.push({
                    lat: baseLat + perpLat * offset,
                    lng: baseLng + perpLng * offset
                });
            }
        }
        return result;
    }

    async function fetchOsrmRoutes(url, timeoutMs = 15000) {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), timeoutMs);
        try {
            const response = await fetch(url, {
                headers: { "Accept": "application/json" },
                signal: controller.signal
            });
            if (!response.ok) throw new Error(`Routing service HTTP ${response.status}`);
            const data = await response.json();
            if (data.code !== "Ok" || !Array.isArray(data.routes)) return [];
            return data.routes;
        } finally {
            clearTimeout(timer);
        }
    }

    async function mapWithConcurrency(items, worker, concurrency = 4) {
        const results = new Array(items.length);
        let cursor = 0;

        async function runner() {
            while (true) {
                const index = cursor++;
                if (index >= items.length) return;
                try {
                    results[index] = await worker(items[index], index);
                } catch (error) {
                    results[index] = [];
                    console.warn("Route candidate failed:", error);
                }
            }
        }

        await Promise.all(
            Array.from({ length: Math.min(concurrency, items.length) }, runner)
        );
        return results.flat();
    }

    function routePointSamples(route, maxSamples = 28) {
        const coords = Array.isArray(route?.geometry?.coordinates)
            ? route.geometry.coordinates
            : [];
        if (!coords.length) return [];

        const count = Math.min(maxSamples, coords.length);
        const samples = [];
        for (let i = 0; i < count; i++) {
            const index = count === 1
                ? 0
                : Math.round((i / (count - 1)) * (coords.length - 1));
            const point = coords[index];
            if (Array.isArray(point) && point.length >= 2) {
                samples.push([Number(point[1]), Number(point[0])]);
            }
        }
        return samples;
    }

        const trafficLayers = new Map();

    function toggleTrafficLayer(map) {
        if (!map) return;

        let layer = trafficLayers.get(map._safeRoadMapKey);
        if (!layer) {
            layer = L.tileLayer(
                "http://localhost:8080/api/traffic/tile/{z}/{x}/{y}.png",
                {
                    tileSize: 256,
                    opacity: 0.75,
                    maxZoom: 19,
                    attribution: "Traffic overlay"
                }
            );
            layer.on("tileerror", () => {
                setStatus(
                    map._safeRoadMapKey || "dashboard",
                    "Traffic overlay is not available from the backend yet. Routes still work normally."
                );
            });
            trafficLayers.set(map._safeRoadMapKey, layer);
        }

        if (map.hasLayer(layer)) {
            map.removeLayer(layer);
            setStatus(map._safeRoadMapKey || "dashboard", "Live traffic overlay OFF.");
        } else {
            layer.addTo(map);
            setStatus(map._safeRoadMapKey || "dashboard", "Live traffic overlay ON.");
        }
    }

    function ensureRouteUi(config, map) {
        const controlHost = document.querySelector(
            config.toolbar === "dashboard"
                ? ".dashboard-map-controls"
                : ".map-controls"
        );

        if (!controlHost) return;

        const makeButton = (id, label, title, extraClass = "") => {
            let button = document.getElementById(id);
            if (button) return button;
            button = document.createElement("button");
            button.id = id;
            button.type = "button";
            button.title = title;
            button.textContent = label;
            button.className = extraClass;
            controlHost.appendChild(button);
            return button;
        };

        const direction = makeButton(
            config.direction,
            "➤",
            "Show all practical driving routes from your GPS location to the selected destination",
            "safe-road-direction-btn"
        );
        direction.style.width = "42px";
        direction.style.height = "42px";

        let traffic = document.getElementById(config.toolbar + "TrafficBtn");
        if (!traffic) {
            traffic = makeButton(
                config.toolbar + "TrafficBtn",
                "🚦",
                "Toggle optional live traffic overlay",
                "safe-road-traffic-btn"
            );
            traffic.addEventListener("click", () => toggleTrafficLayer(map));
        }
    }

    function renderRouteCards(routes, mapKey = "dashboard", recommendedIndex = -1) {
        const containers = document.querySelectorAll(
            "#safe-route .route-options, .route-options[data-safe-road-routes]"
        );
        if (!containers.length) return;

        const hasRecommendation = Number.isInteger(recommendedIndex)
            && recommendedIndex >= 0
            && recommendedIndex < routes.length;

        const html = routes.map((route, index) => {
            const riskClass = String(route.risk || "ANALYZING").includes("LOW") ? "route-low" :
                String(route.risk || "").includes("MEDIUM") ? "route-medium" :
                String(route.risk || "").includes("ANALYZING") ? "" : "route-high";
            const recommended = hasRecommendation && index === recommendedIndex;
            return `
                <div class="safe-road-route-card ${recommended ? "recommended" : ""}" data-route-index="${index}">
                    <div class="safe-road-route-title">
                        <b>Route ${String.fromCharCode(65 + index)}${recommended ? " ★ RECOMMENDED" : ""}</b>
                        <span class="${riskClass}">${escapeHtml(route.risk)}</span>
                    </div>
                    <div class="safe-road-route-meta">
                        <span>${route.distanceKm.toFixed(1)} km</span>
                        <span>${Math.max(1, Math.round(route.durationMin))} min</span>
                        <span>Safety ${route.safetyScore}/100</span>
                    </div>
                    <div class="safe-road-route-factors">
                        🏫 ${route.nearbySchools} &nbsp; ⛰️ ${route.nearbyHills} &nbsp; 🐘 ${route.nearbyWildlife}
                    </div>
                    <button type="button" class="safe-road-route-focus" data-route-index="${index}">Show this route on map</button>
                </div>
            `;
        }).join("");

        containers.forEach(container => {
            container.innerHTML = html;
            container.dataset.safeRoadRoutes = "true";
            container.querySelectorAll(".safe-road-route-focus").forEach(button => {
                button.addEventListener("click", () => {
                    const index = Number(button.dataset.routeIndex);
                    const map = maps.get(mapKey) || maps.get("safeRoadLiveMap") || maps.values().next().value;
                    const layers = map?._safeRoadRouteLayers || [];
                    layers.forEach((layer, layerIndex) => {
                        layer.setStyle({
                            weight: layerIndex === index ? 10 : 6,
                            opacity: layerIndex === index ? 1 : 0.35
                        });
                    });
                    if (map && layers[index]) {
                        const bounds = layers[index].getBounds();
                        if (bounds.isValid()) map.fitBounds(bounds, { padding: [40, 40] });
                        layers[index].openPopup?.();
                    }
                });
            });
        });

        const savedRoutes = routes.map(route => ({
            name: route.name,
            distanceKm: route.distanceKm,
            durationMin: route.durationMin,
            safetyScore: route.safetyScore,
            risk: route.risk,
            nearbySchools: route.nearbySchools,
            nearbyHills: route.nearbyHills,
            nearbyWildlife: route.nearbyWildlife,
            source: route.source
        }));
        localStorage.setItem("saferoad.routeCandidates", JSON.stringify(savedRoutes));
        localStorage.setItem("saferoad.routeUpdatedAt", new Date().toISOString());
    }

    function ensureRouteCardsStyle() {
        if (document.getElementById("safeRoadRouteStyle")) return;
        const style = document.createElement("style");
        style.id = "safeRoadRouteStyle";
        style.textContent = `
            .safe-road-direction-btn { background:#1877ff !important; border-color:#4b92ff !important; }
            .safe-road-traffic-btn { font-size:16px !important; }
            .safe-road-route-card { padding:16px !important; margin-bottom:10px; border:1px solid rgba(255,255,255,.12); border-radius:14px; background:rgba(5,24,43,.78); color:#fff; }
            .safe-road-route-card.recommended { border-color:#18d98b; box-shadow:0 0 0 1px rgba(24,217,139,.15); }
            .safe-road-route-title { display:flex; justify-content:space-between; gap:12px; align-items:center; }
            .safe-road-route-meta { display:flex; gap:18px; flex-wrap:wrap; margin-top:8px; opacity:.9; }
            .safe-road-route-factors { margin-top:8px; font-size:12px; opacity:.7; }
            .safe-road-route-focus { margin-top:10px; border:1px solid rgba(255,255,255,.16); background:#0b2945; color:#fff; border-radius:9px; padding:8px 11px; cursor:pointer; }
            .route-low { color:#18d98b; font-weight:800; }
            .route-medium { color:#ffb020; font-weight:800; }
            .route-high { color:#ff405d; font-weight:800; }
        `;
        document.head.appendChild(style);
    }

    function routePointsForApi(route, maxPoints = 8) {
        const coords = Array.isArray(route?.geometry?.coordinates) ? route.geometry.coordinates : [];
        if (!coords.length) return [];
        const count = Math.min(maxPoints, coords.length);
        const points = [];
        for (let i = 0; i < count; i++) {
            const idx = count === 1 ? 0 : Math.round((i / (count - 1)) * (coords.length - 1));
            const c = coords[idx];
            if (Array.isArray(c) && c.length >= 2 && Number.isFinite(Number(c[0])) && Number.isFinite(Number(c[1]))) {
                points.push({ lat: Number(c[1]), lon: Number(c[0]) });
            }
        }
        return points;
    }

    async function fetchRouteLiveFactors(routeModels) {
        const routes = routeModels.map((m, i) => ({
            id: `route-${i + 1}`,
            name: m.name,
            distanceKm: Number(m.distanceKm || 0),
            durationMin: Number(m.durationMin || 0),
            points: routePointsForApi(m.raw, 8)
        }));
        const body = JSON.stringify({ routes });
        const [weatherResponse, trafficResponse] = await Promise.allSettled([
            fetch("http://localhost:8080/api/weather/route", { method: "POST", headers: { "Content-Type": "application/json" }, body }),
            fetch("http://localhost:8080/api/traffic/route", { method: "POST", headers: { "Content-Type": "application/json" }, body })
        ]);
        let weatherRoutes = [], trafficRoutes = [];
        if (weatherResponse.status === "fulfilled" && weatherResponse.value.ok) {
            const data = await weatherResponse.value.json();
            weatherRoutes = Array.isArray(data.routes) ? data.routes : [];
        }
        if (trafficResponse.status === "fulfilled" && trafficResponse.value.ok) {
            const data = await trafficResponse.value.json();
            trafficRoutes = Array.isArray(data.routes) ? data.routes : [];
        }
        routeModels.forEach((m, i) => {
            const id = `route-${i + 1}`;
            m.weather = weatherRoutes.find(x => x.id === id || x.name === m.name) || null;
            m.traffic = trafficRoutes.find(x => x.id === id || x.name === m.name) || null;
        });
        window.routeWeatherData = weatherRoutes;
        window.routeTrafficData = trafficRoutes;
        return { weatherRoutes, trafficRoutes };
    }

    // ============================================================
    // OSM ROAD METADATA
    // ============================================================
    // Uses real OpenStreetMap road tags for the generated route
    // samples. We do NOT invent/randomize risk values.
    // ============================================================

    function osmRoadTypeFromHighway(highway) {
        const value = String(highway || "").toLowerCase();
        if (["motorway", "motorway_link", "trunk", "trunk_link"].includes(value)) {
            return "highway";
        }
        if (["unclassified", "road", "track"].includes(value)) {
            return "rural";
        }
        if ([
            "primary", "primary_link", "secondary", "secondary_link",
            "tertiary", "tertiary_link", "residential", "living_street",
            "service"
        ].includes(value)) {
            return "urban";
        }
        return "urban";
    }

    function osmSurfaceForAccidentModel(surface) {
        const value = String(surface || "").toLowerCase();
        if (["gravel", "fine_gravel", "compacted"].includes(value)) return "Gravel roads";
        if (["dirt", "earth", "ground", "mud", "sand"].includes(value)) return "Earth roads";
        if (["asphalt", "concrete", "paved", "paving_stones", "sett"].includes(value)) return "Asphalt roads";
        return "Asphalt roads";
    }

    function routeOsmSamplePoints(route) {
        const coords = Array.isArray(route?.geometry?.coordinates)
            ? route.geometry.coordinates
            : [];
        if (!coords.length) return [];

        const fractions = [0.25, 0.50, 0.75];
        return fractions.map(fraction => {
            const index = Math.min(
                coords.length - 1,
                Math.max(0, Math.round((coords.length - 1) * fraction))
            );
            const point = coords[index];
            if (!Array.isArray(point) || point.length < 2) return null;
            const lon = Number(point[0]);
            const lat = Number(point[1]);
            if (!Number.isFinite(lat) || !Number.isFinite(lon)) return null;
            return { lat, lon };
        }).filter(Boolean);
    }

    function haversineMeters(lat1, lon1, lat2, lon2) {
        const R = 6371000;
        const toRad = value => value * Math.PI / 180;
        const dLat = toRad(lat2 - lat1);
        const dLon = toRad(lon2 - lon1);
        const a = Math.sin(dLat / 2) ** 2 +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
        return 2 * R * Math.asin(Math.sqrt(a));
    }

    function parseOsmLanes(value) {
        if (value === undefined || value === null || value === "") return null;
        const match = String(value).match(/\d+/);
        if (!match) return null;
        const lanes = Number(match[0]);
        return Number.isFinite(lanes) && lanes > 0 && lanes <= 20 ? lanes : null;
    }

    function chooseMostCommon(values) {
        const counts = new Map();
        values.filter(Boolean).forEach(value => {
            const key = String(value);
            counts.set(key, (counts.get(key) || 0) + 1);
        });
        let winner = null;
        let best = -1;
        counts.forEach((count, key) => {
            if (count > best) {
                best = count;
                winner = key;
            }
        });
        return winner;
    }

    async function fetchRouteOsmMetadata(routeModels) {
        if (!Array.isArray(routeModels) || !routeModels.length) return;

        const sampleRefs = [];
        routeModels.forEach((model, routeIndex) => {
            routeOsmSamplePoints(model.raw).forEach(point => {
                sampleRefs.push({ routeIndex, ...point });
            });
        });

        if (!sampleRefs.length) return;

        const clauses = [];
        sampleRefs.forEach(point => {
            clauses.push(
                `way(around:70,${point.lat},${point.lon})[highway][highway!~"^(footway|path|cycleway|pedestrian|steps|bridleway)$"];`
            );
            clauses.push(
                `node(around:90,${point.lat},${point.lon})[highway="traffic_signals"];`
            );
        });

        const query = `[out:json][timeout:45];(${clauses.join("")} );out tags center;`;

        try {
            const response = await fetch("https://overpass-api.de/api/interpreter", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8" },
                body: "data=" + encodeURIComponent(query)
            });

            if (!response.ok) {
                throw new Error(`OSM metadata HTTP ${response.status}`);
            }

            const data = await response.json();
            const elements = Array.isArray(data.elements) ? data.elements : [];
            const ways = elements.filter(item => item?.type === "way" && item?.tags?.highway);
            const signals = elements.filter(item =>
                item?.type === "node" && item?.tags?.highway === "traffic_signals"
            );

            routeModels.forEach((model, routeIndex) => {
                const points = routeOsmSamplePoints(model.raw);
                const matchedWays = [];
                let signalNearby = false;

                points.forEach(point => {
                    let nearestWay = null;
                    let nearestDistance = Infinity;

                    ways.forEach(way => {
                        const centerLat = Number(way?.center?.lat);
                        const centerLon = Number(way?.center?.lon);
                        if (!Number.isFinite(centerLat) || !Number.isFinite(centerLon)) return;
                        const distance = haversineMeters(point.lat, point.lon, centerLat, centerLon);
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearestWay = way;
                        }
                    });

                    if (nearestWay && nearestDistance <= 100) {
                        matchedWays.push(nearestWay);
                    }

                    const signalDistance = signals.reduce((best, signal) => {
                        const lat = Number(signal?.lat);
                        const lon = Number(signal?.lon);
                        if (!Number.isFinite(lat) || !Number.isFinite(lon)) return best;
                        return Math.min(best, haversineMeters(point.lat, point.lon, lat, lon));
                    }, Infinity);
                    if (signalDistance <= 90) signalNearby = true;
                });

                const roadTypes = matchedWays.map(way => osmRoadTypeFromHighway(way.tags?.highway));
                const lanes = matchedWays.map(way => parseOsmLanes(way.tags?.lanes)).filter(Number.isFinite);
                const surfaces = matchedWays.map(way => way.tags?.surface).filter(Boolean);
                const highwayTags = matchedWays.map(way => String(way.tags?.highway || "").toLowerCase()).filter(Boolean);
                const roadNames = matchedWays.map(way => way.tags?.name).filter(Boolean);
                const maxSpeeds = matchedWays.map(way => way.tags?.maxspeed).filter(Boolean);

                const averageLanes = lanes.length
                    ? Math.round(lanes.reduce((sum, value) => sum + value, 0) / lanes.length)
                    : 2;

                const roadType = chooseMostCommon(roadTypes) || "urban";
                const surface = chooseMostCommon(surfaces) || "asphalt";

                model.osm = {
                    roadType,
                    lanes: averageLanes,
                    trafficSignal: signalNearby ? 1 : 0,
                    surface,
                    highway: chooseMostCommon(highwayTags) || "unknown",
                    roadName: chooseMostCommon(roadNames) || "",
                    maxspeed: chooseMostCommon(maxSpeeds) || "",
                    wayIds: [...new Set(matchedWays.map(way => way.id).filter(Boolean))]
                };

                // Store route-specific values for the trained model and UI.
                model.road_type = roadType;
                model.lanes = averageLanes;
                model.traffic_signal = signalNearby ? 1 : 0;
                model.surface = surface;
                model.roadName = model.osm.roadName;
                model.maxspeed = model.osm.maxspeed;
            });

            window.routeOsmMetadata = routeModels.map(model => model.osm || null);
            return routeModels;
        } catch (error) {
            console.warn("OSM route metadata unavailable; using model-compatible fallbacks:", error);
            routeModels.forEach(model => {
                model.osm = model.osm || {
                    roadType: "urban",
                    lanes: 2,
                    trafficSignal: 0,
                    surface: "asphalt",
                    highway: "unknown",
                    roadName: "",
                    maxspeed: "",
                    wayIds: []
                };
                model.road_type = model.road_type || "urban";
                model.lanes = Number(model.lanes || 2);
                model.traffic_signal = Number(model.traffic_signal || 0);
                model.surface = model.surface || "asphalt";
            });
        }
    }

    function buildSafeRouteFeatures(model) {
        const now = new Date();
        const hour = now.getHours();
        const days = ["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"];
        const routeSamples = routePointSamples(model.raw, 3);
        const midpoint = routeSamples[Math.floor(routeSamples.length / 2)] || [0, 0];
        // OSRM coordinates are [longitude, latitude].
        const midpointLongitude = Number(midpoint[0] || 0);
        const midpointLatitude = Number(midpoint[1] || 0);
        const weatherPoint = model.weather?.weatherPoints?.[0] || {};
        const congestion = Number(model.traffic?.trafficCongestionPercent || 0);
        const weather = weatherPoint.condition || "Clear";
        const trafficDensity = congestion >= 70 ? "High" : congestion >= 40 ? "Medium" : "Low";
        return {
            city: model.city || "Unknown", state: model.state || "Unknown", latitude: midpointLatitude, longitude: midpointLongitude,
            hour, day_of_week: days[now.getDay()], is_weekend: [0,6].includes(now.getDay()) ? 1 : 0,
            road_type: model.road_type || model.osm?.roadType || "urban",
            lanes: Number(model.lanes || model.osm?.lanes || 2),
            traffic_signal: Number(model.traffic_signal ?? model.osm?.trafficSignal ?? 0),
            weather, visibility: weatherPoint.visibility || "Good",
            temperature: Number(weatherPoint.temperature ?? 25),
            traffic_density: trafficDensity,
            is_peak_hour: ((hour >= 8 && hour <= 11) || (hour >= 17 && hour <= 20)) ? 1 : 0,
            festival: "No",
            accident_model_features: {
                Time: `${String(hour).padStart(2,"0")}:00`,
                Day_of_week: days[now.getDay()],
                Age_band_of_driver: "Unknown", Sex_of_driver: "Unknown", Driving_experience: "Unknown",
                Type_of_vehicle: "Unknown", Service_year_of_vehicle: "Unknown", Defect_of_vehicle: "Unknown",
                Area_accident_occured: (model.road_type || "urban"),
                Lanes_or_Medians: Number(model.lanes || 2) >= 4 ? "Four lanes" : "Two-way",
                Road_allignment: "Tangent-road",
                Types_of_Junction: Number(model.traffic_signal ?? 0) === 1 ? "Signalized junction" : "No junction",
                Road_surface_type: osmSurfaceForAccidentModel(model.surface || model.osm?.surface),
                Road_surface_conditions: weather.toLowerCase().includes("rain") ? "Wet" : "Dry",
                Light_conditions: (hour >= 6 && hour < 18) ? "Daylight" : "Darkness - lights lit",
                Weather_conditions: weather, Type_of_collision: "Unknown", Number_of_vehicles_involved: 2,
                Vehicle_movement: "Unknown", Cause_of_accident: "Unknown"
            }
        };
    }

    async function requestIntegratedSafeRouteAI(routeModels) {
        const payload = {
            routes: routeModels.map((m, i) => ({
                id: `route-${i + 1}`, name: m.name,
                distanceKm: Number(m.distanceKm || 0), durationMin: Number(m.durationMin || 0),
                coordinates: Array.isArray(m.raw?.geometry?.coordinates) ? m.raw.geometry.coordinates : [],
                features: buildSafeRouteFeatures(m)
            })),
            accidentRadiusKm: 1.0
        };
        const response = await fetch("http://localhost:8080/api/ai/safe-route", {
            method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload)
        });
        if (!response.ok) throw new Error(`Safe Route AI HTTP ${response.status}`);
        const data = await response.json();
        if (!Array.isArray(data.routes)) throw new Error(data.error || "Invalid AI response");
        data.routes.forEach(ai => {
            const model = routeModels.find(m => `route-${m.index + 1}` === ai.id || m.name === ai.name);
            if (!model) return;
            model.ai = ai;
            model.safetyScore = Math.round(100 - Number(ai.finalRiskPercentage || 0));
            model.risk = `${ai.finalRiskLevel || "MEDIUM"} RISK`;
        });
        window.aiSafeRouteData = data;
        return data;
    }

    function renderIntegratedRouteFactors(routeModels, recommendedIndex) {
        let panel = document.getElementById("routeRiskDetails");
        if (!panel) {
            panel = document.createElement("div"); panel.id = "routeRiskDetails";
            panel.style.cssText = "margin-top:12px;display:grid;gap:8px;";
            const host = document.querySelector("#safe-route .route-options, .route-options[data-safe-road-routes]") || document.querySelector(".map-side-panel");
            (host?.parentElement || document.body).appendChild(panel);
        }
        panel.innerHTML = routeModels.map((m, i) => {
            const a = m.ai || {};
            const w = m.weather || {}; const t = m.traffic || {};
            const active = i === recommendedIndex ? "border:2px solid #18d98b;" : "";
            return `<div style="padding:12px;border:1px solid rgba(255,255,255,.12);border-radius:12px;background:rgba(5,24,43,.78);color:#fff;${active}">
                <b>${escapeHtml(m.name)} ${i === recommendedIndex ? "★ RECOMMENDED" : ""}</b>
                <div style="margin-top:5px;font-size:12px;opacity:.85">AI Risk: ${Number(a.finalRiskPercentage ?? 0).toFixed(1)}% • ${escapeHtml(a.finalRiskLevel || m.risk)}</div>
                <div style="margin-top:4px;font-size:12px;opacity:.72">🌦️ Weather: ${escapeHtml(w.weatherRiskLevel || "Unavailable")} • 🚦 Traffic: ${escapeHtml(t.trafficRiskLevel || "Unavailable")} • 🏫 ${m.nearbySchools} • ⛰️ ${m.nearbyHills} • 🐘 ${m.nearbyWildlife}</div>
                <div style="margin-top:4px;font-size:11px;opacity:.62">Historical crashes: ${Number(a.nearbyAccidentCount || 0)} • Segment samples: ${Number(a.aiSegmentCount || 0)}</div>
            </div>`;
        }).join("");
    }

    function renderRouteLines(map, routeModels, recommendedIndex = -1) {
        if (!map) return null;

        // Remove only route graphics; geofence/alert layers stay untouched.
        if (map._safeRoadRouteGroup && map.hasLayer(map._safeRoadRouteGroup)) {
            map.removeLayer(map._safeRoadRouteGroup);
        }
        if (map._safeRoadRouteGroup) {
            map._safeRoadRouteGroup.clearLayers?.();
        }

        const routeStyles = [
            "#1877ff", "#16a34a", "#f59e0b", "#a855f7", "#ef4444",
            "#06b6d4", "#84cc16", "#f97316", "#ec4899", "#64748b"
        ];

        const group = L.featureGroup();
        const layers = [];

        routeModels.forEach((model, index) => {
            const recommended = index === recommendedIndex;
            const line = L.geoJSON(model.raw.geometry, {
                style: {
                    color: routeStyles[index] || routeStyles[0],
                    weight: recommended ? 8 : 5,
                    opacity: recommended ? 0.95 : 0.52,
                    lineCap: "round",
                    lineJoin: "round"
                }
            });

            line._safeRoadRouteLayer = true;
            line.bindPopup(`
                <div style="min-width:230px">
                    <strong>🚗 ${escapeHtml(model.name || `Route ${String.fromCharCode(65 + index)}`)}${recommended ? " ★ RECOMMENDED" : ""}</strong><br>
                    ${Number(model.distanceKm || 0).toFixed(1)} km • ${Math.max(1, Math.round(Number(model.durationMin || 0)))} min<br>
                    ${Number.isFinite(Number(model.safetyScore)) && Number(model.safetyScore) > 0 ? `AI safety score: ${Number(model.safetyScore).toFixed(0)}/100<br>` : ""}
                    <small>${escapeHtml(model.risk || "ANALYZING")} • ${escapeHtml(model.source || "Routing service")}</small>
                </div>
            `);

            line.on("click", () => {
                layers.forEach((other, otherIndex) => {
                    other.setStyle({
                        weight: otherIndex === index ? 10 : 6,
                        opacity: otherIndex === index ? 1 : 0.35
                    });
                });
                setStatus(
                    map._safeRoadMapKey || "dashboard",
                    `${model.name}: ${Number(model.distanceKm || 0).toFixed(1)} km • ${Math.round(Number(model.durationMin || 0))} min${model.risk ? ` • ${model.risk}` : ""}`
                );
            });

            line.addTo(group);
            layers.push(line);
        });

        group.addTo(map);
        map._safeRoadRouteGroup = group;
        map._safeRoadRouteLayers = layers;
        map._safeRoadRoutes = routeModels;
        return group;
    }

    async function enrichRoutesInBackground(
            map, baseRouteModels, startLat, startLng, endLat, endLng, start, end, mapKey, runId) {
        try {
            const waypoints = buildCorridorWaypoints(
                startLat,
                startLng,
                endLat,
                endLng
            );

            const corridorRoutes = await mapWithConcurrency(
                waypoints,
                waypoint => {
                    const url =
                        `https://router.project-osrm.org/route/v1/driving/${start};` +
                        `${waypoint.lng},${waypoint.lat};${end}` +
                        `?overview=full&geometries=geojson&steps=false&alternatives=false&continue_straight=false`;
                    return fetchOsrmRoutes(url);
                },
                4
            );

            if (map._safeRoadDirectionRunId !== runId) return;

            let routes = deduplicateRoutes([
                ...baseRouteModels.map(model => model.raw),
                ...corridorRoutes
            ]).filter(route =>
                Array.isArray(route?.geometry?.coordinates) &&
                route.geometry.coordinates.length >= 2 &&
                Number(route.distance) > 0
            );

            if (!routes.length) routes = baseRouteModels.map(model => model.raw);

            const shortestDistanceKm = Math.min(...routes.map(r => Number(r.distance) / 1000));
            routes = routes
                .filter(route => Number(route.distance) / 1000 <= shortestDistanceKm * 2.0)
                .sort((a, b) => Number(a.distance) - Number(b.distance))
                .slice(0, 10);

            const routeModels = routes.map((route, index) => ({
                raw: route,
                index,
                distanceKm: Number(route.distance) / 1000,
                durationMin: Number(route.duration) / 60,
                safetyScore: 0,
                risk: "ANALYZING",
                nearbySchools: 0,
                nearbyHills: 0,
                nearbyWildlife: 0,
                source: "OSRM"
            }));

            routeModels.forEach((model, index) => {
                model.name = `Route ${String.fromCharCode(65 + index)}`;
            });

            window.generatedRoutes = routeModels;
            window.safeRoadGeneratedRoutesMapKey = domMapKey(mapKey);
            bindGlobalZoneButtons();

            renderRouteLines(map, routeModels, -1);
            const expandedBounds = map._safeRoadRouteGroup?.getBounds();
            if (expandedBounds?.isValid()) map.fitBounds(expandedBounds, { padding: [45, 45] });
            setStatus(mapKey, `${routeModels.length} practical routes found. Checking weather, traffic and AI risk...`);

            // Geofencing is an alert subsystem only. It does NOT affect route score.
            await loadRouteSafetyZones(routeModels, mapKey);

            if (map._safeRoadDirectionRunId !== runId) return;

            try {
                const liveFactors = await fetchRouteLiveFactors(routeModels);
                await fetchRouteOsmMetadata(routeModels);
                const completeLiveData = routeModels.every(model =>
                    model.weather?.weatherPoints?.length > 0 &&
                    model.traffic?.trafficPoints?.length > 0
                );
                if (!completeLiveData) {
                    throw new Error("Live weather/traffic data is incomplete for one or more routes.");
                }

                const aiData = await requestIntegratedSafeRouteAI(routeModels);
                if (map._safeRoadDirectionRunId !== runId) return;

                let recommendedIndex = routeModels.findIndex(
                    m => `route-${m.index + 1}` === aiData.recommendedRouteId
                );

                if (recommendedIndex < 0) {
                    const ranked = routeModels
                        .map((model, index) => ({ index, risk: Number(model.ai?.finalRiskPercentage) }))
                        .filter(item => Number.isFinite(item.risk))
                        .sort((a, b) => a.risk - b.risk);
                    recommendedIndex = ranked.length ? ranked[0].index : -1;
                }

                if (recommendedIndex < 0) {
                    throw new Error("AI service returned no usable route recommendation.");
                }

                window.safeRoadRecommendedRoute = recommendedIndex;
                renderRouteLines(map, routeModels, recommendedIndex);
                renderIntegratedRouteFactors(routeModels, recommendedIndex);
                renderRouteCards(routeModels, mapKey, recommendedIndex);

                setStatus(
                    mapKey,
                    `${routeModels.length} practical routes analysed using live weather, traffic and AI risk.`
                );
            } catch (aiError) {
                console.warn("Integrated live-factor/AI analysis unavailable:", aiError);
                renderRouteLines(map, routeModels, -1);
                renderRouteCards(routeModels, mapKey);
                setStatus(
                    mapKey,
                    `${routeModels.length} practical routes found. Live weather/traffic/AI analysis unavailable.`
                );
            }
        } catch (error) {
            console.warn("Background route expansion failed:", error);
            if (map._safeRoadDirectionRunId !== runId) return;
            setStatus(mapKey, `${baseRouteModels.length} route(s) shown. Additional route search failed.`);
        }
    }

    async function drawDirection(map) {
        if (!searchDestination) {
            setStatus(map._safeRoadMapKey || "dashboard", "Search a destination first.");
            return;
        }

        if (!currentLocation) {
            setStatus(map._safeRoadMapKey || "dashboard", "Waiting for GPS location before starting direction...");
            startGps();
            return;
        }

        removeRouteLayersOnly(map);

        const directionButton = document.getElementById(
            map._safeRoadMapKey === "dashboard" ? "dashboardDirectionBtn" : "directionBtn"
        );

        if (directionButton) {
            directionButton.disabled = true;
            directionButton.textContent = "…";
        }

        const runId = (map._safeRoadDirectionRunId || 0) + 1;
        map._safeRoadDirectionRunId = runId;
        const mapKey = map._safeRoadMapKey || "dashboard";
        const start = `${currentLocation.lng},${currentLocation.lat}`;
        const end = `${searchDestination.lng},${searchDestination.lat}`;

        try {
            setStatus(mapKey, "Finding fastest route...");

            const baseUrl =
                `https://router.project-osrm.org/route/v1/driving/${start};${end}` +
                `?overview=full&geometries=geojson&steps=false&alternatives=true&continue_straight=false`;

            // IMPORTANT: show the first real route as soon as the routing request finishes.
            const baseRoutes = await fetchOsrmRoutes(baseUrl);
            if (map._safeRoadDirectionRunId !== runId) return;

            const routes = deduplicateRoutes(baseRoutes).filter(route =>
                Array.isArray(route?.geometry?.coordinates) &&
                route.geometry.coordinates.length >= 2 &&
                Number(route.distance) > 0
            );

            if (!routes.length) throw new Error("No driving route found");

            const baseRouteModels = routes.slice(0, 3).map((route, index) => ({
                raw: route,
                index,
                name: `Route ${String.fromCharCode(65 + index)}`,
                distanceKm: Number(route.distance) / 1000,
                durationMin: Number(route.duration) / 60,
                safetyScore: 0,
                risk: "ANALYZING",
                nearbySchools: 0,
                nearbyHills: 0,
                nearbyWildlife: 0,
                source: "OSRM"
            }));

            window.generatedRoutes = baseRouteModels;
            window.safeRoadGeneratedRoutesMapKey = domMapKey(mapKey);
            window.safeRoadRecommendedRoute = -1;
            bindGlobalZoneButtons();

            renderRouteLines(map, baseRouteModels, -1);
            const baseBounds = map._safeRoadRouteGroup?.getBounds();
            if (baseBounds?.isValid()) map.fitBounds(baseBounds, { padding: [45, 45] });

            renderRouteCards(baseRouteModels, mapKey, -1);
            setStatus(mapKey, `${baseRouteModels.length} route(s) shown. Finding more practical alternatives...`);

            // The expensive alternative search + weather + traffic + AI runs after
            // the initial route is already visible, so Direction no longer feels frozen.
            if (directionButton) {
                directionButton.disabled = false;
                directionButton.textContent = "➤";
            }

            void enrichRoutesInBackground(map, baseRouteModels, currentLocation.lat, currentLocation.lng, searchDestination.lat, searchDestination.lng, start, end, mapKey, runId);
        } catch (error) {
            console.error("Direction failed:", error);
            setStatus(mapKey, "Direction could not be calculated. Check internet connection and try again.");
            if (directionButton) {
                directionButton.disabled = false;
                directionButton.textContent = "➤";
            }
        }
    }

    function removeRouteLayersOnly(map) {
        if (!map) return;
        if (Array.isArray(map._safeRoadRouteLayers)) {
            map._safeRoadRouteLayers.forEach(layer => {
                if (layer && map.hasLayer(layer)) map.removeLayer(layer);
            });
        }
        map._safeRoadRouteLayers = [];
        map._safeRoadRoutes = [];
        if (map._safeRoadRouteGroup) {
            if (map.hasLayer(map._safeRoadRouteGroup)) map.removeLayer(map._safeRoadRouteGroup);
            map._safeRoadRouteGroup.clearLayers?.();
        }
        map._safeRoadRouteGroup = null;
    }

    function setupControls(config, map) {
        map._safeRoadMapKey = config.toolbar === "dashboard" ? "dashboard" : "live";

        document.getElementById(config.zoomIn)?.addEventListener(
            "click",
            () => map.zoomIn()
        );

        document.getElementById(config.zoomOut)?.addEventListener(
            "click",
            () => map.zoomOut()
        );

        document.getElementById(config.clearSelected)?.addEventListener(
            "click",
            clearSelectedLocation
        );

        document.getElementById(config.direction)?.addEventListener(
            "click",
            () => drawDirection(map)
        );

        document.getElementById(config.locate)?.addEventListener(
            "click",
            () => {
                if (currentLocation) {
                    map.setView(
                        [currentLocation.lat, currentLocation.lng],
                        Math.max(map.getZoom(), 15)
                    );
                } else {
                    startGps();
                }
            }
        );
    }

    function setupMapClick(map) {
        map.on("click", async event => {
            const { lat, lng } = event.latlng;

            if (map._safeRoadSelectedMarker && map.hasLayer(map._safeRoadSelectedMarker)) {
                map.removeLayer(map._safeRoadSelectedMarker);
            }

            const marker = L.marker([lat, lng], {
                icon: L.divIcon({
                    className: "",
                    html: '<div class="safe-road-click-marker"></div>',
                    iconSize: [22, 22],
                    iconAnchor: [11, 11]
                })
            }).addTo(map);

            map._safeRoadSelectedMarker = marker;
            selectedLocationMarker = marker;

            const basePopup = `
                <div style="min-width:240px">
                    <strong>📍 Selected Location</strong><br>
                    <span>Loading place name...</span><br>
                    <small>Latitude: ${lat.toFixed(6)}<br>Longitude: ${lng.toFixed(6)}</small>
                </div>
            `;
            marker.bindPopup(basePopup).openPopup();

            const nameEl = document.getElementById("selectedLocationName");
            const coordsEl = document.getElementById("selectedLocationCoords");
            if (nameEl) nameEl.textContent = "Finding place...";
            if (coordsEl) coordsEl.textContent = `${lat.toFixed(6)}, ${lng.toFixed(6)}`;

            const placeName = await reverseGeocode(lat, lng);

            marker.setPopupContent(`
                <div style="min-width:240px">
                    <strong>📍 ${escapeHtml(placeName.split(",")[0] || placeName)}</strong>
                    <div style="margin-top:5px;font-size:12px">${escapeHtml(placeName)}</div>
                    <div style="margin-top:6px">Latitude: ${lat.toFixed(6)}</div>
                    <div>Longitude: ${lng.toFixed(6)}</div>
                </div>
            `);

            if (nameEl) nameEl.textContent = placeName.split(",")[0] || placeName;
            if (coordsEl) coordsEl.textContent = `${lat.toFixed(6)}, ${lng.toFixed(6)}`;
        });
    }


    // ============================================================
    // SAFE ROAD — CURRENT WEATHER FROM OUR SPRING BOOT API ONLY
    // ============================================================
    // Frontend talks ONLY to our Spring Boot weather endpoint:
    //   GET http://localhost:8080/api/weather?lat=<lat>&lon=<lon>
    //
    // No OpenWeather/other weather provider is called from this JS.
    // Spring Boot is responsible for its configured weather provider.
    // ============================================================

    let currentWeatherRequestInFlight = false;
    let lastCurrentWeatherFetchAt = 0;
    let lastCurrentWeatherLat = null;
    let lastCurrentWeatherLon = null;

    function findCurrentWeatherCard() {
        const candidates = document.querySelectorAll(".summary-card");
        for (const card of candidates) {
            const label = card.querySelector("small");
            if (label && String(label.textContent || "").trim().toLowerCase() === "weather") {
                return card;
            }
        }

        // Fallback for layouts where the summary card class is different.
        const labels = document.querySelectorAll("small, span, strong");
        for (const label of labels) {
            if (String(label.textContent || "").trim().toLowerCase() !== "weather") continue;
            const card = label.closest("article, .card, .summary-card, .panel, div");
            if (card) return card;
        }

        return null;
    }

    function updateCurrentWeatherCard(payload) {
        const card = findCurrentWeatherCard();
        if (!card) return;

        const valueEl = card.querySelector("strong");
        const detailEl = card.querySelector("p");

        if (!valueEl || !detailEl) return;

        const condition =
            payload?.weather?.[0]?.main ||
            payload?.weather?.[0]?.description ||
            "Unavailable";

        const description =
            payload?.weather?.[0]?.description || "";

        const rawTemp = payload?.main?.temp;
        const rawHumidity = payload?.main?.humidity;

        const temperature = Number(rawTemp);
        const humidity = Number(rawHumidity);

        valueEl.textContent = condition;

        if (Number.isFinite(temperature) && Number.isFinite(humidity)) {
            detailEl.textContent =
                `${temperature.toFixed(1)}°C • Humidity ${Math.round(humidity)}%`;
        } else {
            detailEl.textContent = description || "Weather data unavailable";
        }

        // Keep a precise source/data marker for debugging without exposing
        // provider-specific details in the UI.
        card.dataset.safeRoadWeatherSource = "spring-boot-api";
        card.dataset.safeRoadWeatherUpdatedAt = new Date().toISOString();
    }

    function setCurrentWeatherUnavailable(message = "Weather data unavailable") {
        const card = findCurrentWeatherCard();
        if (!card) return;

        const valueEl = card.querySelector("strong");
        const detailEl = card.querySelector("p");

        if (valueEl) valueEl.textContent = "Unavailable";
        if (detailEl) detailEl.textContent = message;

        card.dataset.safeRoadWeatherSource = "spring-boot-api";
    }

    async function fetchCurrentWeatherFromOwnApi(lat, lon, force = false) {
        if (!Number.isFinite(Number(lat)) || !Number.isFinite(Number(lon))) return;
        if (currentWeatherRequestInFlight) return;

        const now = Date.now();
        const movedMeters = (
            lastCurrentWeatherLat == null ||
            lastCurrentWeatherLon == null
        )
            ? Infinity
            : haversine(
                lastCurrentWeatherLat,
                lastCurrentWeatherLon,
                Number(lat),
                Number(lon)
            );

        // Refresh every 5 minutes, or sooner when the user moved > 1 km.
        if (!force &&
            now - lastCurrentWeatherFetchAt < 5 * 60 * 1000 &&
            movedMeters < 1000) {
            return;
        }

        currentWeatherRequestInFlight = true;
        lastCurrentWeatherFetchAt = now;
        lastCurrentWeatherLat = Number(lat);
        lastCurrentWeatherLon = Number(lon);

        try {
            const url =
                `http://localhost:8080/api/weather?lat=${encodeURIComponent(Number(lat))}` +
                `&lon=${encodeURIComponent(Number(lon))}`;

            const response = await fetch(url, {
                method: "GET",
                headers: {
                    "Accept": "application/json"
                },
                cache: "no-store"
            });

            if (!response.ok) {
                throw new Error(`Spring Boot weather API HTTP ${response.status}`);
            }

            const data = await response.json();
            updateCurrentWeatherCard(data);

            console.log("SafeRoad current weather from Spring Boot API:", {
                lat: Number(lat),
                lon: Number(lon),
                data
            });
        } catch (error) {
            console.warn("SafeRoad current weather API failed:", error);
            setCurrentWeatherUnavailable("Weather data unavailable");
        } finally {
            currentWeatherRequestInFlight = false;
        }
    }

    function startGps() {
        if (!navigator.geolocation) {
            setStatus("dashboard", "GPS is not supported by this browser.");
            setStatus("live", "GPS is not supported by this browser.");
            return;
        }

        if (gpsWatchId != null) return;

        setStatus("dashboard", "Requesting live GPS location...");
        setStatus("live", "Requesting live GPS location...");

        const handlePosition = position => {
            currentLocation = {
                lat: position.coords.latitude,
                lng: position.coords.longitude,
                heading: Number.isFinite(position.coords.heading)
                    ? position.coords.heading
                    : null,
                accuracy: position.coords.accuracy
            };

            updateUserMarkers();
            updateLocationText();

            // Live weather for the user's current GPS position.
            // This talks only to our Spring Boot /api/weather endpoint.
            void fetchCurrentWeatherFromOwnApi(
                currentLocation.lat,
                currentLocation.lng
            );

            if (!gpsAutoCentered) {
                gpsAutoCentered = true;
                maps.forEach(map => {
                    map.setView(
                        [currentLocation.lat, currentLocation.lng],
                        Math.max(map.getZoom(), 15),
                        { animate: true }
                    );
                });
            }

            if (!gpsNameRequestInFlight) {
                gpsNameRequestInFlight = true;
                reverseGeocode(currentLocation.lat, currentLocation.lng)
                    .then(placeName => {
                        const shortName = placeName.split(",")[0] || placeName;
                        document.getElementById("dashboardLocationName")?.replaceChildren(document.createTextNode(shortName));
                        document.getElementById("currentLocationName")?.replaceChildren(document.createTextNode(shortName));
                    })
                    .catch(() => {})
                    .finally(() => { gpsNameRequestInFlight = false; });
            }

            setStatus("dashboard", `Live GPS • accuracy ±${Math.round(currentLocation.accuracy || 0)} m`);
            setStatus("live", `Live GPS • accuracy ±${Math.round(currentLocation.accuracy || 0)} m`);
        };

        const handleError = error => {
            console.warn("GPS error:", error);
            const messages = {
                1: "Location permission denied. Allow location access for 127.0.0.1:5500.",
                2: "GPS position unavailable. Check Windows/browser location services.",
                3: "GPS request timed out. Retrying..."
            };
            const message = messages[error.code] || "GPS unavailable. Map still works.";
            setStatus("dashboard", message);
            setStatus("live", message);
        };

        navigator.geolocation.getCurrentPosition(
            handlePosition,
            handleError,
            { enableHighAccuracy: true, maximumAge: 0, timeout: 30000 }
        );

        gpsWatchId = navigator.geolocation.watchPosition(
            handlePosition,
            handleError,
            { enableHighAccuracy: true, maximumAge: 5000, timeout: 30000 }
        );

        if (!alertTimer) {
            alertTimer = setInterval(checkNearbyAlerts, 7500);
        }
    }

    function updateUserMarkers() {
        if (!currentLocation) return;

        maps.forEach(map => {
            if (!map._safeRoadUserMarker) {
                map._safeRoadUserMarker = L.circleMarker(
                    [currentLocation.lat, currentLocation.lng],
                    {
                        radius: 8,
                        color: "#fff",
                        weight: 3,
                        fillColor: "#1877ff",
                        fillOpacity: 1
                    }
                ).addTo(map).bindPopup("📍 Your current GPS location");
            } else {
                map._safeRoadUserMarker.setLatLng([
                    currentLocation.lat,
                    currentLocation.lng
                ]);
            }
        });
    }

    function updateLocationText() {
        const text = `${currentLocation.lat.toFixed(5)}° N, ${currentLocation.lng.toFixed(5)}° E`;

        document.querySelectorAll("[data-live-gps]").forEach(el => {
            el.textContent = text;
        });

        const dashboardCoords = document.getElementById("dashboardLocationCoords");
        const currentCoords = document.getElementById("currentLocationCoords");
        if (dashboardCoords) dashboardCoords.textContent = text;
        if (currentCoords) currentCoords.textContent = `${text} • ±${Math.round(currentLocation.accuracy || 0)} m`;
    }

    async function checkNearbyAlerts() {
        if (!currentLocation || alertRequestInFlight) return;
        alertRequestInFlight = true;

        const params = new URLSearchParams({
            latitude: currentLocation.lat,
            longitude: currentLocation.lng,
            radius: "10000"
        });

        try {
            // Use the locally imported India OSM/PostGIS safety data for
            // geofence alerts. Fall back to the legacy DB endpoint when the
            // local endpoint is unavailable.
            let nearby = [];
            const localResponse = await fetch(`${API_BASE}/local-nearby?${params}`);
            if (localResponse.ok) {
                const localData = await localResponse.json();
                nearby = Array.isArray(localData) ? localData.map(zone => ({
                    zone,
                    distanceMeters: Number(zone.distanceMeters || 0),
                    distanceKm: Number(zone.distanceMeters || 0) / 1000
                })) : [];
            } else {
                const legacyResponse = await fetch(`${API_BASE}/nearby?${params}`);
                if (!legacyResponse.ok) return;
                nearby = await legacyResponse.json();
            }

            for (const item of nearby) {
                const zone = item.zone;
                const distanceMeters = Number(item.distanceMeters);
                const distanceKm = Number(item.distanceKm);

                if (!isZoneAhead(zone, currentLocation)) continue;

                const zoneId = String(zone.id ?? `${zone.sourceType || "osm"}:${zone.sourceId || `${zone.latitude}:${zone.longitude}`}`);
                const previous = lastAlertByZone.get(zoneId);
                const now = Date.now();

                const entered = distanceMeters <= Math.max(
                    Number(zone.radius || 0),
                    100
                );

                // Alert on first detection, threshold crossing, or after cooldown.
                const threshold =
                    distanceMeters <= 500 ? 500 :
                    distanceMeters <= 2000 ? 2000 :
                    distanceMeters <= 5000 ? 5000 :
                    10000;

                const shouldAlert =
                    !previous ||
                    previous.threshold !== threshold ||
                    (now - previous.time > 120000 && distanceMeters < previous.distanceMeters);

                if (shouldAlert) {
                    showAlert(zone, distanceKm, entered);
                    lastAlertByZone.set(zoneId, {
                        threshold,
                        distanceMeters,
                        time: now
                    });
                }
            }

            // Forget zones that are now well outside the alert radius.
            for (const [id, data] of lastAlertByZone.entries()) {
                const stillNearby = nearby.some(x => String(x.zone?.id ?? `${x.zone?.sourceType || "osm"}:${x.zone?.sourceId || `${x.zone?.latitude}:${x.zone?.longitude}`}`) === id);
                if (!stillNearby) {
                    lastAlertByZone.delete(id);
                }
            }
        } catch (error) {
            console.warn("Safety alert request failed:", error);
        } finally {
            alertRequestInFlight = false;
        }
    }

    function requestNotificationPermission() {
        if ("Notification" in window
                && Notification.permission === "default") {
            Notification.requestPermission().catch(() => {});
        }
    }

    // Any real user gesture unlocks browser speech without changing the
    // existing HTML. No test sound is played here.
    document.addEventListener("pointerdown", unlockVoiceAlerts, { capture: true, passive: true, once: true });
    document.addEventListener("keydown", unlockVoiceAlerts, { capture: true, passive: true, once: true });

    function setupSearch(map) {
        const input = document.getElementById("mapSearchInput") || document.getElementById("destinationInput");
        const button = document.getElementById("mapSearchBtn") || document.getElementById("findRouteBtn");
        const results = document.getElementById("mapSearchResults") || (() => {
            const box = document.createElement("div");
            box.id = "mapSearchResults";
            box.className = "map-search-results";
            if (input?.parentElement) input.parentElement.appendChild(box);
            return box;
        })();

        if (!input || input.dataset.bound === "true") return;
        input.dataset.bound = "true";

        let timer = null;
        let controller = null;

        const hideResults = () => {
            results.style.display = "none";
        };

        const renderResults = places => {
            if (!places.length) {
                results.innerHTML = '<div class="map-search-empty">No place found. Try a city, landmark or address.</div>';
                results.style.display = "block";
                return;
            }

            results.innerHTML = places.map((place, index) => `
                <button type="button" class="map-search-result" data-index="${index}">
                    <strong>${escapeHtml(place.name || place.display_name.split(",")[0])}</strong>
                    <small>${escapeHtml(place.display_name)}</small>
                </button>
            `).join("");
            results.style.display = "block";

            results.querySelectorAll(".map-search-result").forEach(result => {
                result.addEventListener("click", () => {
                    const place = places[Number(result.dataset.index)];
                    if (!place) return;
                    const lat = Number(place.lat);
                    const lon = Number(place.lon);
                    const name = place.display_name || place.name || "Searched location";
                    input.value = name;
                    hideResults();
                    setSearchDestination(map, lat, lon, name);
                });
            });
        };

        async function search(autoSelectFirst = false) {
            const q = input.value.trim();
            if (q.length < 2) {
                results.innerHTML = "";
                hideResults();
                return;
            }

            if (controller) controller.abort();
            controller = new AbortController();

            results.innerHTML = '<div class="map-search-empty">Searching OpenStreetMap...</div>';
            results.style.display = "block";

            try {
                const url = "https://nominatim.openstreetmap.org/search?" +
                    new URLSearchParams({
                        q,
                        format: "jsonv2",
                        addressdetails: "1",
                        limit: "6",
                        countrycodes: "in",
                        "accept-language": "en"
                    });

                const response = await fetch(url, {
                    method: "GET",
                    headers: { "Accept": "application/json" },
                    signal: controller.signal
                });

                if (!response.ok) throw new Error(`Search service ${response.status}`);
                const places = await response.json();
                renderResults(places);

                if (autoSelectFirst && places.length) {
                    const place = places[0];
                    const lat = Number(place.lat);
                    const lon = Number(place.lon);
                    const name = place.display_name || place.name || q;
                    input.value = name;
                    hideResults();
                    setSearchDestination(map, lat, lon, name);
                }
            } catch (error) {
                if (error.name === "AbortError") return;
                console.error("Place search failed:", error);
                results.innerHTML = '<div class="map-search-empty">Search failed. Check your internet connection and try again.</div>';
                results.style.display = "block";
            }
        }

        input.addEventListener("input", () => {
            clearTimeout(timer);
            timer = setTimeout(() => search(false), 300);
        });

        button?.addEventListener("click", () => search(true));

        input.addEventListener("keydown", event => {
            if (event.key === "Enter") {
                event.preventDefault();
                search(true);
            }
            if (event.key === "Escape") hideResults();
        });

        document.addEventListener("click", event => {
            if (!event.target.closest(".map-header-search")) hideResults();
        });
    }

    function installZoneButtonDelegation() {
        if (document.documentElement.dataset.safeRoadZoneDelegation === "true") return;
        document.documentElement.dataset.safeRoadZoneDelegation = "true";

        document.addEventListener("click", event => {
            const button = event.target?.closest?.("button, [role=button], [data-zone], [data-type]");
            if (!button) return;

            const key = zoneButtonKey(button);
            if (!key || button.dataset.safeRoadZoneBound === "true") return;

            const mapKey = routeMapKey();
            if (!mapKey || !window.generatedRoutes?.length) {
                event.preventDefault();
                event.stopPropagation();
                setStatus(statusMapKey(mapKey || "roadSafetyMap"), "First calculate a route.");
                return;
            }

            event.preventDefault();
            event.stopPropagation();
            toggleRouteZoneLayer(mapKey, key, button);
        }, true);
    }

    function initializeMap(config) {
        const element = document.getElementById(config.id);
        if (!element || typeof L === "undefined") return;

        const map = L.map(element, {
            zoomControl: false,
            preferCanvas: true
        }).setView(DEFAULT_CENTER, DEFAULT_ZOOM);
        roadNameLayer.addTo(map);

       const MAPTILER_KEY = "AIjMLb00jID8SbW9SdMs";

L.tileLayer(
    `https://api.maptiler.com/maps/streets-v2/{z}/{x}/{y}.png?key=${MAPTILER_KEY}`,
    {
        tileSize: 512,
        zoomOffset: -1,
        maxZoom: 20,
        attribution: '&copy; MapTiler &copy; OpenStreetMap contributors'
    }
).addTo(map);

        maps.set(config.id, map);

        layerState.set(config.id, {
            // Layers are created but NOT added initially.
            // School / Hill / Wildlife start OFF.
            schools: L.layerGroup(),
            hills: L.layerGroup(),
            wildlife: L.layerGroup()
        });

        getVisibility(config.id);
        ensureRouteCardsStyle();
        ensureRouteUi(config, map);
        setupToolbar(config.id, config.toolbar);
        setupControls(config, map);
        setupMapClick(map);

        let loadTimer;
        const scheduleLoad = () => {
            clearTimeout(loadTimer);
            loadTimer = setTimeout(
                () => loadDatabaseZones(config.id),
                450
            );
        };

        map.on("moveend zoomend", scheduleLoad);

        setTimeout(() => {
            map.invalidateSize();
            // Load zone data for alerts and route safety, but keep the three
            // visual layers OFF until the user turns them on.
            loadDatabaseZones(config.id);
            applyVisibility(config.id);
            setStatus(
                config.toolbar === "dashboard" ? "dashboard" : "live",
                "Map ready — Schools, Hills and Wildlife are OFF. Turn them on when needed."
            );
        }, 150);

        if (config.id === "safeRoadLiveMap") {
            setupSearch(map);
        }
    }

    function initialize() {
        ensureAlertUi();

        installZoneButtonDelegation();
        CONFIGS.forEach(initializeMap);
        bindGlobalZoneButtons();

        startGps();

        requestNotificationPermission();

        // Allow another script/page section to request a map refresh.
        window.safeRoadMap = {
            refresh: () => maps.forEach((_, key) => loadDatabaseZones(key)),
            getCurrentLocation: () => currentLocation
        };
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initialize);
    } else {
        initialize();
    }
})();
