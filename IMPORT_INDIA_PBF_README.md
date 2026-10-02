# SafeRoad Fast India OSM Import

This replaces the slow Overpass grid importer for the **one-time India-wide preload**.

## Why this is faster

Instead of sending hundreds/thousands of public Overpass requests, SafeRoad downloads the official Geofabrik India `.osm.pbf` extract once and reads it locally with Pyosmium. Geofabrik currently provides `india-latest.osm.pbf`; the official page describes it as suitable for Osmium/Osmosis/etc. and currently lists it at about 1.6 GB. See the official page: https://download.geofabrik.de/asia/india.html

## Requirements

- PostgreSQL running
- SafeRoad Spring Boot backend running on `http://localhost:8080`
- Python 3.10+
- Internet only for the one-time PBF download

The script installs the `osmium` Python package automatically if it is missing.

## Run

From the project root:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\IMPORT_INDIA_PBF.ps1
```

The first run downloads the India PBF into:

```text
data\india-latest.osm.pbf
```

Then it streams matching OSM objects locally and sends batches to:

```text
POST /api/safety-zones/import/geojson-batch
```

## Imported OSM tags

### Schools

- `amenity=school`

### Hills

- `natural=hill`
- `natural=peak`
- `natural=mountain`
- `natural=cliff`

### Wildlife / protected areas

- `leisure=nature_reserve`
- `leisure=wildlife_park`
- `boundary=protected_area`
- `boundary=national_park`
- `boundary=wildlife_park`

Only objects actually mapped with these OSM tags are imported; the importer does not invent missing locations.

## Resume / safety

Progress is stored in:

```text
data\india-pbf-import\pbf-import-progress.txt
```

The database import is idempotent using the OSM source/type/id identity, so running the importer again will not intentionally create duplicate OSM records.

## Important

Do not run the old `IMPORT_INDIA_GRID.ps1` at the same time. Stop it with `Ctrl+C` before starting this importer.
