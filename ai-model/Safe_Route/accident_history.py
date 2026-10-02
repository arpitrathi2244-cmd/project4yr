import os
import numpy as np
import pandas as pd

EARTH_RADIUS_KM = 6371.0088


def load_accident_history(csv_path: str) -> pd.DataFrame:
    if not os.path.exists(csv_path):
        raise FileNotFoundError(f"Accident history CSV not found: {csv_path}")

    df = pd.read_csv(csv_path)
    required = {"latitude", "longitude"}
    missing = required - set(df.columns)
    if missing:
        raise ValueError(f"Accident history CSV missing columns: {sorted(missing)}")

    df = df.copy()
    df["latitude"] = pd.to_numeric(df["latitude"], errors="coerce")
    df["longitude"] = pd.to_numeric(df["longitude"], errors="coerce")
    df = df.dropna(subset=["latitude", "longitude"]).reset_index(drop=True)
    return df


def _haversine_km(lat1, lon1, lats2, lons2):
    lat1 = np.radians(lat1)
    lon1 = np.radians(lon1)
    lat2 = np.radians(lats2)
    lon2 = np.radians(lons2)

    dlat = lat2 - lat1
    dlon = lon2 - lon1

    a = np.sin(dlat / 2.0) ** 2 + np.cos(lat1) * np.cos(lat2) * np.sin(dlon / 2.0) ** 2
    return 2.0 * EARTH_RADIUS_KM * np.arcsin(np.sqrt(np.clip(a, 0.0, 1.0)))


def route_history_risk(route_coordinates, accident_df: pd.DataFrame, radius_km: float = 1.0):
    """
    route_coordinates: iterable of [longitude, latitude] pairs (GeoJSON/MapTiler style)
    radius_km: accident considered near route when <= this distance from a route point

    Returns transparent history metrics rather than claiming that zero accidents means safe.
    """
    if not route_coordinates:
        return {
            "historyRiskScore": 0.0,
            "nearbyAccidentCount": 0,
            "closestAccidentDistanceKm": None,
            "historicalRiskLevel": "NO_DATA"
        }

    points = []
    for item in route_coordinates:
        if isinstance(item, (list, tuple)) and len(item) >= 2:
            try:
                lon = float(item[0])
                lat = float(item[1])
            except (TypeError, ValueError):
                continue
        elif isinstance(item, dict):
            try:
                lat = float(item.get("lat", item.get("latitude")))
                lon = float(item.get("lon", item.get("lng", item.get("longitude"))))
            except (TypeError, ValueError):
                continue
        else:
            continue

        if -90 <= lat <= 90 and -180 <= lon <= 180:
            points.append((lat, lon))

    if not points:
        return {
            "historyRiskScore": 0.0,
            "nearbyAccidentCount": 0,
            "closestAccidentDistanceKm": None,
            "historicalRiskLevel": "NO_DATA"
        }

    lats = accident_df["latitude"].to_numpy(dtype=float)
    lons = accident_df["longitude"].to_numpy(dtype=float)

    # For each accident, calculate its distance to the nearest route point.
    nearest_distances = np.full(len(accident_df), np.inf, dtype=float)
    for lat, lon in points:
        distances = _haversine_km(lat, lon, lats, lons)
        nearest_distances = np.minimum(nearest_distances, distances)

    nearby_mask = nearest_distances <= radius_km
    nearby_distances = nearest_distances[nearby_mask]
    nearby_count = int(nearby_mask.sum())

    if nearby_count == 0:
        return {
            "historyRiskScore": 0.0,
            "nearbyAccidentCount": 0,
            "closestAccidentDistanceKm": round(float(nearest_distances.min()), 3),
            "historicalRiskLevel": "NO_RECORDED_CRASH_NEARBY"
        }

    # Give a little more influence to crashes closer to the route.
    # Saturating transform prevents very dense historical clusters from dominating everything.
    weights = 1.0 / (1.0 + nearby_distances)
    weighted_history = float(weights.sum())
    history_score = float(np.clip(1.0 - np.exp(-weighted_history / 3.0), 0.0, 1.0))

    if history_score < 0.25:
        level = "LOW"
    elif history_score < 0.55:
        level = "MEDIUM"
    else:
        level = "HIGH"

    return {
        "historyRiskScore": round(history_score, 4),
        "nearbyAccidentCount": nearby_count,
        "closestAccidentDistanceKm": round(float(nearby_distances.min()), 3),
        "historicalRiskLevel": level
    }
