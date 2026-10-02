# SafeRoad - OSM Database Safety Zones Final Setup

This package is prepared from the uploaded SafeRoad project.

## Replaced files

### Backend
Replace these files:

1. `backend/src/main/java/com/saferoad/entity/SafetyZone.java`
2. `backend/src/main/java/com/saferoad/repository/SafetyZoneRepository.java`
3. `backend/src/main/java/com/saferoad/service/IndianSafetyZoneService.java`
4. `backend/src/main/java/com/saferoad/controller/SafetyZoneController.java`
5. `backend/src/main/java/com/saferoad/controller/dto/SafetyZoneResponse.java`
6. `backend/src/main/java/com/saferoad/config/SecurityConfig.java`

### Frontend
Replace:

7. `frontend/js/live-map.js`

The existing `frontend/index.html` and `frontend/css/map.css` are intentionally preserved because the uploaded project already contains the Leaflet/map UI and toolbar.

## What changed

- OSM objects imported by the backend are saved in PostgreSQL.
- OSM source/type/id are stored to prevent duplicate imports.
- OSM way/relation geometry is stored in `geometry_json`.
- Wildlife areas can render as actual polygons.
- Hill/cliff geometry can render as lines.
- Schools render as OSM-based markers.
- The map does NOT create random/fake safety markers.
- Map layer visibility is independent from the alert engine.
- Turning Schools/Hills/Wildlife OFF hides them only from the map.
- The GPS alert engine still asks `/api/safety-zones/nearby` and can alert even when the layer is hidden.
- Clicking anywhere on the map places a selected-location marker.
- The map uses the saved database data rather than repeatedly querying Overpass.
- A regional OSM bounding-box import endpoint has been added.

## New backend APIs

### Nearby alert data

`GET /api/safety-zones/nearby?latitude=29.03&longitude=78.21&radius=10000`

This endpoint is independent of map visibility.

### Map bounding-box data

`GET /api/safety-zones/bbox?south=...&west=...&north=...&east=...`

The frontend uses this while moving/zooming the map.

### Regional OSM import

`POST /api/safety-zones/import/bbox?south=...&west=...&north=...&east=...&zoneType=SCHOOL`

Allowed types:

- `SCHOOL`
- `HILL_AREA`
- `WILDLIFE`

Do NOT send a giant whole-India school query to public Overpass servers. Import India in manageable regional chunks and keep the imported data in PostgreSQL.

## Existing import APIs still work

```powershell
Invoke-WebRequest `
  -UseBasicParsing `
  -Method POST `
  -Uri "http://localhost:8080/api/safety-zones/import/schools?latitude=29.03892&longitude=78.21405&radius=10000"
```

```powershell
Invoke-WebRequest `
  -UseBasicParsing `
  -Method POST `
  -Uri "http://localhost:8080/api/safety-zones/import/hills?latitude=30.4598&longitude=78.0664&radius=10000"
```

```powershell
Invoke-WebRequest `
  -UseBasicParsing `
  -Method POST `
  -Uri "http://localhost:8080/api/safety-zones/import/wildlife?latitude=29.205101&longitude=78.0857214&radius=10000"
```

## Build

From:

`backend`

run:

```powershell
mvn clean compile
```

Then:

```powershell
mvn spring-boot:run
```

The uploaded machine used Maven before, so use the same Maven installation you already use for this project.

## Frontend

Run the frontend with your existing Live Server/local server.

Then hard refresh:

`Ctrl + Shift + R`

Allow browser GPS permission when prompted.

## Important behavior

Example:

```text
Schools = OFF
Wildlife = ON
Hills = ON
```

If the user is 8 km from a school:

- School is NOT drawn on the map.
- Backend still returns the school from `/nearby`.
- GPS alert can still say:
  "School Zone Ahead — 8 km."

Turning Schools ON makes the saved school data visible again.

## Alert distances

The frontend checks the backend every 10 seconds while GPS is available.

Current thresholds:

- 10 km: early warning
- 5 km: warning
- 2 km: strong warning
- 500 m: immediate warning
- entering the configured zone radius: entry warning

If GPS heading is available, the frontend also checks whether the zone is in front of the user's current travel direction. If heading is unavailable, distance-based alerting still works.

## Important data limitation

OpenStreetMap coverage is community-mapped. "All India" therefore means all OSM objects that have actually been mapped and successfully imported into your database.

Do not expect one public Overpass request to safely return every school in India. Use regional imports and store the results in PostgreSQL.

Also, hill terrain is not equivalent to a single OSM `natural=hill` tag. This implementation imports OSM hill/peak/mountain/cliff features. A future terrain/elevation layer can be added separately for continuous slope/steepness analysis.

## Database migration

The project already has:

`spring.jpa.hibernate.ddl-auto=update`

The new fields should therefore be added automatically when the backend starts.

The new fields include:

- `alert_enabled`
- `source`
- `source_type`
- `source_id`
- `geometry_json`

Existing safety-zone rows are retained.

## Verification

After starting the backend:

```powershell
Invoke-WebRequest `
  -UseBasicParsing `
  -Method GET `
  -Uri "http://localhost:8080/api/safety-zones"
```

Check nearby alert data:

```powershell
Invoke-WebRequest `
  -UseBasicParsing `
  -Method GET `
  -Uri "http://localhost:8080/api/safety-zones/nearby?latitude=29.03892&longitude=78.21405&radius=10000"
```

Then open the dashboard map.

1. Zoom into an area where imported zones exist.
2. Confirm the database-backed zones appear.
3. Turn a layer OFF.
4. Confirm its map objects disappear.
5. Keep GPS enabled and move near a stored zone.
6. Confirm the alert can still appear while that layer is OFF.
7. Turn the layer ON again and confirm the stored zone becomes visible.

