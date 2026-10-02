package com.saferoad.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/** Reads the locally imported OSM safety-zone table in PostGIS. */
@Service
public class LocalOsmSafetyZoneService {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public LocalOsmSafetyZoneService() {
        this.jdbcUrl = env("OSM_PG_URL", "jdbc:postgresql://127.0.0.1:5433/osm_india");
        this.username = env("OSM_PG_USER", "osmuser");
        this.password = requiredEnv("OSM_PG_PASSWORD");
    }

    public List<Map<String, Object>> findAlongRoute(
            List<List<Double>> routeLatLng, double radiusMeters) {

        validateRoute(routeLatLng, radiusMeters);
        String wkt = toLineStringWkt(routeLatLng);

        String sql = ""
                + "WITH route AS ("
                + " SELECT ST_SetSRID(ST_GeomFromText(?), 4326)::geography AS geom"
                + ") "
                + "SELECT z.osm_id, z.osm_type, z.name, z.zone_type, "
                + " ST_Y(ST_Centroid(z.geom)) AS latitude, "
                + " ST_X(ST_Centroid(z.geom)) AS longitude, "
                + " z.source, z.source_type, z.source_id, "
                + " COALESCE(z.tags->>'description', '') AS description, "
                + " ST_Distance(z.geom::geography, route.geom) AS distance_meters "
                + "FROM osm_safety_zones z CROSS JOIN route "
                + "WHERE z.zone_type IN ('SCHOOL','HILL_AREA','WILDLIFE') "
                + "AND ST_DWithin(z.geom::geography, route.geom, ?) "
                + "ORDER BY distance_meters ASC "
                + "LIMIT 5000";

        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, wkt);
            statement.setDouble(2, radiusMeters);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    String osmType = rs.getString("osm_type");
                    long osmId = rs.getLong("osm_id");

                    item.put("id", osmType + ":" + osmId);
                    item.put("name", rs.getString("name"));
                    item.put("zoneType", rs.getString("zone_type"));
                    item.put("latitude", rs.getDouble("latitude"));
                    item.put("longitude", rs.getDouble("longitude"));
                    item.put("source", rs.getString("source"));
                    item.put("sourceType", rs.getString("source_type"));
                    item.put("sourceId", osmId);
                    item.put("description", rs.getString("description"));
                    item.put("distanceMeters", rs.getDouble("distance_meters"));
                    result.add(item);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Could not query local OSM PostGIS database at " + jdbcUrl, ex);
        }

        return result;
    }

    public List<Map<String, Object>> findNearby(
            double latitude, double longitude, double radiusMeters) {

        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid latitude or longitude.");
        }
        if (!Double.isFinite(radiusMeters) || radiusMeters <= 0 || radiusMeters > 10000) {
            throw new IllegalArgumentException("radius must be between 1 and 10000 meters.");
        }

        String sql = ""
                + "WITH p AS ("
                + " SELECT ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography AS geom"
                + ") "
                + "SELECT z.osm_id, z.osm_type, z.name, z.zone_type, "
                + " ST_Y(ST_Centroid(z.geom)) AS latitude, "
                + " ST_X(ST_Centroid(z.geom)) AS longitude, "
                + " z.source, z.source_type, z.source_id, "
                + " COALESCE(z.tags->>'description', '') AS description, "
                + " ST_Distance(z.geom::geography, p.geom) AS distance_meters "
                + "FROM osm_safety_zones z CROSS JOIN p "
                + "WHERE z.zone_type IN ('SCHOOL','HILL_AREA','WILDLIFE') "
                + "AND ST_DWithin(z.geom::geography, p.geom, ?) "
                + "ORDER BY distance_meters ASC LIMIT 200";

        List<Map<String, Object>> result = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setDouble(1, longitude);
            statement.setDouble(2, latitude);
            statement.setDouble(3, radiusMeters);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    String osmType = rs.getString("osm_type");
                    long osmId = rs.getLong("osm_id");
                    item.put("id", osmType + ":" + osmId);
                    item.put("name", rs.getString("name"));
                    item.put("zoneType", rs.getString("zone_type"));
                    item.put("latitude", rs.getDouble("latitude"));
                    item.put("longitude", rs.getDouble("longitude"));
                    item.put("source", rs.getString("source"));
                    item.put("sourceType", rs.getString("source_type"));
                    item.put("sourceId", osmId);
                    item.put("description", rs.getString("description"));
                    item.put("distanceMeters", rs.getDouble("distance_meters"));
                    item.put("distanceKm", rs.getDouble("distance_meters") / 1000.0);
                    result.add(item);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Could not query local OSM PostGIS database at " + jdbcUrl, ex);
        }

        return result;
    }

    private static String toLineStringWkt(List<List<Double>> routeLatLng) {
        StringBuilder wkt = new StringBuilder("LINESTRING(");
        for (int i = 0; i < routeLatLng.size(); i++) {
            if (i > 0) wkt.append(',');
            List<Double> point = routeLatLng.get(i);
            // Frontend sends [latitude, longitude]; WKT expects [longitude latitude].
            wkt.append(point.get(1)).append(' ').append(point.get(0));
        }
        return wkt.append(')').toString();
    }

    private static void validateRoute(List<List<Double>> routeLatLng, double radiusMeters) {
        if (routeLatLng == null || routeLatLng.size() < 2) {
            throw new IllegalArgumentException("At least two route coordinates are required.");
        }
        if (!Double.isFinite(radiusMeters) || radiusMeters <= 0 || radiusMeters > 5000) {
            throw new IllegalArgumentException("radiusMeters must be between 1 and 5000.");
        }
        for (List<Double> point : routeLatLng) {
            if (point == null || point.size() < 2
                    || point.get(0) == null || point.get(1) == null
                    || !Double.isFinite(point.get(0)) || !Double.isFinite(point.get(1))
                    || point.get(0) < -90 || point.get(0) > 90
                    || point.get(1) < -180 || point.get(1) > 180) {
                throw new IllegalArgumentException("Invalid route coordinate.");
            }
        }
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required.");
        }
        return value;
    }
}
