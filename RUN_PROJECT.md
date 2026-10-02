# SafeRoad - Combined Project Run Guide

This build combines the two supplied projects rather than choosing only one.

## What was combined
- Frontend base: richer dashboard/live map from Project 1.
- Route/safety frontend: Project 1 live-map with real OSM safety zones and practical OSRM alternatives.
- AI/backend base: Project 2 Safe Route AI, Accident Risk AI, Weather API, Traffic API and PostgreSQL integration.
- Safety data backend: Project 1 OSM live/import/bbox/nearby safety-zone services.
- Existing trained `.joblib` models and India accident-history dataset are preserved.

## Main pipeline
Current location + destination
-> practical route alternatives
-> OSM safety zones / geofence factors
-> route weather
-> route traffic
-> Safe Route AI + historical accident proximity + Accident Risk model context
-> final LOW/MEDIUM/HIGH risk per route
-> recommended route on the map and Safe Route page

## Backend
Requirements: Java 17+, Maven, PostgreSQL.

Set environment variables from `.env.example` in the shell/environment before starting Spring Boot.

Database default: `road_safety_db`.
Backend port: `8080`.

Run from `backend`:
`mvn spring-boot:run`

## Python AI
Python 3.10+ recommended.

Install:
`pip install -r ai-model/Accident_Risk/requirements.txt`

Run integrated AI from `ai-model/Safe_Route`:
`python safe_route_api.py`

AI port: `5001`.

The API uses the existing trained Safe Route and Accident Risk models; it does not retrain them at runtime.

## Frontend
Serve the `frontend` folder from a local HTTP server (do not open `index.html` with `file://`).
Example:
`python -m http.server 5500 -d frontend`

Open `http://localhost:5500/`.

## External data
- OSRM is used for practical route alternatives.
- Nominatim is used for destination search.
- Spring Boot Weather API uses the configured OpenWeather key.
- Spring Boot Traffic API uses the configured TomTom key.
- Safety zones can come from the PostgreSQL database and live/imported OSM data.

## Validation note
JavaScript syntax and Python syntax were checked in the build environment. Full Maven/runtime API testing could not be executed here because Maven and the Python Flask dependency stack are not installed in the execution environment. Do not treat this note as a claim of live external-service verification.
