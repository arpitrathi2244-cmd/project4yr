# Frontend Developer Guide

## Main dashboard
`index.html` contains the project shell and all eight module sections.

## Shared frontend
- `css/style.css` — main visual system and dashboard layout.
- `js/app.js` — navigation, toast messages, SOS UI, authentication/profile integration.
- `js/live-map.js` — single map implementation used by the dashboard and Live Map section.
- `js/route-navigation.js` — Module 2 route preview, GPS and map logic.

## Current map stack
The project uses Leaflet with OpenStreetMap tiles. Browser geolocation supplies the user's live position when permission is granted. Nominatim is used for address/destination search and OSRM is used for driving-route preview.

## Next development stage
Finish Module 2 with safe-route scoring using the backend risk/safety-zone data, then connect the remaining modules to Spring Boot APIs.
