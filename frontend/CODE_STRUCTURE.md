# Frontend Code Structure

```text
frontend/
├── index.html
├── css/
│   ├── style.css
│   └── route-navigation.css
├── js/
│   ├── app.js
│   ├── live-map.js
│   ├── login.js
│   ├── register.js
│   └── route-navigation.js
└── pages/
    ├── sign-in.html
    ├── sign-up.html
    ├── risk-prediction.html
    ├── alerts.html
    ├── reports.html
    ├── route-navigation.html
    ├── safe-route.html
    ├── emergency.html
    ├── services.html
    └── analytics.html
```

## Map cleanup

`live-map.js` is now the single frontend map implementation for the dashboard and the Live Map section. The older duplicate `map.js`, `map.html`, `map.css`, and placeholder `live-map.html` were removed.

## Module status

- Module 1 — Live Map: map, OpenStreetMap tiles, GPS tracking, location marker, safety-zone markers and route preview are implemented.
- Module 2 — Route & Navigation: standalone route page now has a real map, GPS and route preview. Safe-route recommendation and turn-by-turn navigation are the next work.
