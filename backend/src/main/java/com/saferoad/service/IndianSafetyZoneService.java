package com.saferoad.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoad.entity.SafetyZone;
import com.saferoad.repository.SafetyZoneRepository;

@Service
public class IndianSafetyZoneService {

    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private static final List<String> OVERPASS_SERVERS = List.of(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
    );

    private final SafetyZoneRepository safetyZoneRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public IndianSafetyZoneService(
            SafetyZoneRepository safetyZoneRepository,
            ObjectMapper objectMapper) {

        this.safetyZoneRepository = safetyZoneRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }


    /**
     * Imports a batch of GeoJSON features produced by the local OSM PBF importer.
     * This path never contacts Overpass; it is intended for the one-time India-wide
     * preload from a Geofabrik .osm.pbf extract.
     */
    @Transactional
    public List<SafetyZone> importGeoJsonBatch(JsonNode features) {
        List<SafetyZone> zones = new ArrayList<>();

        if (features == null || !features.isArray()) {
            return List.of();
        }

        for (JsonNode feature : features) {
            if (!"Feature".equalsIgnoreCase(feature.path("type").asText())) {
                continue;
            }

            JsonNode properties = feature.path("properties");
            String zoneType = detectGeoJsonZoneType(properties);
            if ("OTHER".equals(zoneType)) {
                continue;
            }

            SafetyZone zone = createZoneFromGeoJsonFeature(feature, zoneType);
            if (zone != null) {
                zones.add(zone);
            }
        }

        return saveZones(zones);
    }

    private String detectGeoJsonZoneType(JsonNode properties) {
        if ("school".equalsIgnoreCase(properties.path("amenity").asText(""))) {
            return "SCHOOL";
        }

        String leisure = properties.path("leisure").asText("");
        String boundary = properties.path("boundary").asText("");

        if (List.of("nature_reserve", "wildlife_park").contains(leisure.toLowerCase())
                || List.of("protected_area", "national_park", "wildlife_park")
                        .contains(boundary.toLowerCase())) {
            return "WILDLIFE";
        }

        String natural = properties.path("natural").asText("").toLowerCase();
        if (List.of("hill", "peak", "mountain", "cliff").contains(natural)) {
            return "HILL_AREA";
        }

        return "OTHER";
    }

    private SafetyZone createZoneFromGeoJsonFeature(
            JsonNode feature, String zoneType) {

        JsonNode geometry = feature.path("geometry");
        double[] center = calculateGeoJsonCenter(geometry);
        if (center == null) {
            return null;
        }

        JsonNode properties = feature.path("properties");
        String name = firstNonBlank(
                properties.path("name").asText(""),
                properties.path("official_name").asText(""),
                defaultName(zoneType));

        SafetyZone zone = new SafetyZone();
        zone.setName(name);
        zone.setZoneType(zoneType);
        zone.setLocation("India");
        zone.setLatitude(center[0]);
        zone.setLongitude(center[1]);
        zone.setRadius(defaultRadius(zoneType));
        zone.setSpeedLimit(defaultSpeedLimit(zoneType));
        zone.setActive(true);
        zone.setAlertEnabled(true);
        zone.setSource("OPENSTREETMAP");
        zone.setSourceType(properties.path("@type").asText(null));
        zone.setSourceId(properties.path("@id").asLong(0L));
        zone.setDescription(buildDescription(properties, zoneType));
        zone.setCreatedAt(LocalDateTime.now());

        try {
            zone.setGeometryJson(objectMapper.writeValueAsString(geometry));
        } catch (Exception ignored) {
            zone.setGeometryJson(null);
        }

        return zone;
    }

    private double[] calculateGeoJsonCenter(JsonNode geometry) {
        String type = geometry.path("type").asText("");
        JsonNode coordinates = geometry.path("coordinates");

        if ("Point".equalsIgnoreCase(type) && coordinates.size() >= 2) {
            return new double[] { coordinates.get(1).asDouble(), coordinates.get(0).asDouble() };
        }

        List<double[]> points = new ArrayList<>();
        collectGeoJsonPoints(coordinates, points);
        if (points.isEmpty()) {
            return null;
        }

        double lat = 0;
        double lon = 0;
        for (double[] point : points) {
            lon += point[0];
            lat += point[1];
        }
        return new double[] { lat / points.size(), lon / points.size() };
    }

    private void collectGeoJsonPoints(JsonNode node, List<double[]> points) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        if (node.isArray() && node.size() >= 2
                && node.get(0).isNumber() && node.get(1).isNumber()) {
            points.add(new double[] { node.get(0).asDouble(), node.get(1).asDouble() });
            return;
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                collectGeoJsonPoints(child, points);
            }
        }
    }

    @Transactional
    public List<SafetyZone> fetchAndSaveIndianSafetyZones(
            double latitude, double longitude, double radius) {

        validateCoordinates(latitude, longitude, radius);

        String query = buildGenericQuery(latitude, longitude, radius);
        return saveZones(parseResponse(executeOverpass(query), null));
    }

    @Transactional
    public List<SafetyZone> fetchAndSaveSchoolZones(
            double latitude, double longitude, double radius) {

        validateCoordinates(latitude, longitude, radius);

        String query = buildSchoolQuery(latitude, longitude, radius);
        return saveZones(parseResponse(executeOverpass(query), "SCHOOL"));
    }

    @Transactional
    public List<SafetyZone> fetchAndSaveHillZones(
            double latitude, double longitude, double radius) {

        validateCoordinates(latitude, longitude, radius);

        String query = buildHillQuery(latitude, longitude, radius);
        return saveZones(parseResponse(executeOverpass(query), "HILL_AREA"));
    }

    @Transactional
    public List<SafetyZone> fetchAndSaveWildlifeZones(
            double latitude, double longitude, double radius) {

        validateCoordinates(latitude, longitude, radius);

        String query = buildWildlifeQuery(latitude, longitude, radius);
        return saveZones(parseResponse(executeOverpass(query), "WILDLIFE"));
    }

    /*
     * Bounding-box import is useful for controlled regional syncs.
     * Do not request all Indian schools in one Overpass request.
     */
    /**
     * Fetches OSM objects for map display without writing them to the DB.
     * The frontend uses this only for the current viewport.
     */
    public List<SafetyZone> fetchLiveByBoundingBox(
            double south, double west, double north, double east,
            String requestedType) {

        validateLiveBbox(south, west, north, east);
        String type = normalizeType(requestedType);
        String query = buildBboxQuery(south, west, north, east, type);
        return parseResponse(executeOverpass(query), type);
    }

    @Transactional
    public List<SafetyZone> fetchAndSaveByBoundingBox(
            double south, double west, double north, double east,
            String requestedType) {

        validateBbox(south, west, north, east);

        String type = normalizeType(requestedType);
        String query = buildBboxQuery(south, west, north, east, type);

        return saveZones(parseResponse(executeOverpass(query), type));
    }

    private String buildSchoolQuery(double lat, double lon, double radius) {
        return String.format(
                "[out:json][timeout:90];"
                        + "nwr[amenity=school](around:%f,%f,%f);"
                        + "out geom tags;",
                radius, lat, lon, radius);
    }

    private String buildHillQuery(double lat, double lon, double radius) {
        return String.format(
                "[out:json][timeout:90];("
                        + "nwr[natural=hill](around:%f,%f,%f);"
                        + "nwr[natural=peak](around:%f,%f,%f);"
                        + "nwr[natural=mountain](around:%f,%f,%f);"
                        + "nwr[natural=cliff](around:%f,%f,%f);"
                        + ");out geom tags;",
                radius, lat, lon,
                radius, lat, lon,
                radius, lat, lon,
                radius, lat, lon);
    }

    private String buildWildlifeQuery(double lat, double lon, double radius) {
        return String.format(
                "[out:json][timeout:120];("
                        + "nwr[leisure=nature_reserve](around:%f,%f,%f);"
                        + "nwr[boundary=protected_area](around:%f,%f,%f);"
                        + "nwr[boundary=national_park](around:%f,%f,%f);"
                        + ");out geom tags;",
                radius, lat, lon,
                radius, lat, lon,
                radius, lat, lon);
    }

    private String buildGenericQuery(double lat, double lon, double radius) {
        return String.format(
                "[out:json][timeout:120];("
                        + "nwr[amenity=school](around:%f,%f,%f);"
                        + "nwr[leisure=nature_reserve](around:%f,%f,%f);"
                        + "nwr[boundary=protected_area](around:%f,%f,%f);"
                        + "nwr[boundary=national_park](around:%f,%f,%f);"
                        + "nwr[natural=hill](around:%f,%f,%f);"
                        + "nwr[natural=peak](around:%f,%f,%f);"
                        + "nwr[natural=mountain](around:%f,%f,%f);"
                        + "nwr[natural=cliff](around:%f,%f,%f);"
                        + ");out geom tags;",
                radius, lat, lon,
                radius, lat, lon,
                radius, lat, lon,
                radius, lat, lon,
                radius, lat, lon,
                radius, lat, lon,
                radius, lat, lon,
                radius, lat, lon);
    }

    private String buildBboxQuery(
            double south, double west, double north, double east,
            String type) {

        String bbox = String.format("(%f,%f,%f,%f)", south, west, north, east);

        return switch (type) {
            case "SCHOOL" ->
                    "[out:json][timeout:180];nwr[amenity=school]"
                            + bbox + ";out geom tags;";
            case "HILL_AREA" ->
                    "[out:json][timeout:180];("
                            + "nwr[natural=hill]" + bbox + ";"
                            + "nwr[natural=peak]" + bbox + ";"
                            + "nwr[natural=mountain]" + bbox + ";"
                            + "nwr[natural=cliff]" + bbox + ";"
                            + ");out geom tags;";
            case "WILDLIFE" ->
                    "[out:json][timeout:180];("
                            + "nwr[leisure=nature_reserve]" + bbox + ";"
                            + "nwr[boundary=protected_area]" + bbox + ";"
                            + "nwr[boundary=national_park]" + bbox + ";"
                            + ");out geom tags;";
            default ->
                    throw new IllegalArgumentException(
                            "Type must be SCHOOL, HILL_AREA or WILDLIFE");
        };
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("zoneType is required");
        }
        return type.trim().toUpperCase();
    }

    private String executeOverpass(String query) {
        Exception lastException = null;

        for (String server : OVERPASS_SERVERS) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(server))
                        .timeout(Duration.ofSeconds(180))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("User-Agent", "SafeRoad/1.0")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8)))
                        .build();

                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body() == null || response.body().isBlank()
                            ? "{\"elements\":[]}"
                            : response.body();
                }

                lastException = new RuntimeException(
                        "Overpass server returned HTTP " + response.statusCode());
            } catch (Exception ex) {
                lastException = ex;
            }
        }

        throw new RuntimeException(
                "All Overpass servers failed. "
                        + (lastException == null ? "Unknown error" : lastException.getMessage()),
                lastException);
    }

    private List<SafetyZone> parseResponse(String json, String forcedType) {
        List<SafetyZone> result = new ArrayList<>();

        try {
            JsonNode elements = objectMapper.readTree(json).path("elements");
            if (!elements.isArray()) {
                return result;
            }

            for (JsonNode element : elements) {
                String type = forcedType == null
                        ? detectZoneType(element)
                        : forcedType;

                if ("OTHER".equals(type)) {
                    continue;
                }

                SafetyZone zone = createZoneFromElement(element, type);
                if (zone != null) {
                    result.add(zone);
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Unable to parse Overpass response: " + ex.getMessage(), ex);
        }

        return result;
    }

    private String detectZoneType(JsonNode element) {
        JsonNode tags = element.path("tags");

        if ("school".equalsIgnoreCase(tags.path("amenity").asText(""))) {
            return "SCHOOL";
        }

        String leisure = tags.path("leisure").asText("");
        String boundary = tags.path("boundary").asText("");

        if ("nature_reserve".equalsIgnoreCase(leisure)
                || "protected_area".equalsIgnoreCase(boundary)
                || "national_park".equalsIgnoreCase(boundary)) {
            return "WILDLIFE";
        }

        String natural = tags.path("natural").asText("");
        if (List.of("hill", "peak", "mountain", "cliff")
                .contains(natural.toLowerCase())) {
            return "HILL_AREA";
        }

        return "OTHER";
    }

    private SafetyZone createZoneFromElement(
            JsonNode element, String zoneType) {

        double[] coordinates = extractCoordinates(element);
        if (coordinates == null) {
            return null;
        }

        JsonNode tags = element.path("tags");
        String name = firstNonBlank(
                tags.path("name").asText(""),
                tags.path("official_name").asText(""),
                defaultName(zoneType));

        SafetyZone zone = new SafetyZone();
        zone.setName(name);
        zone.setZoneType(zoneType);
        zone.setLocation("India");
        zone.setLatitude(coordinates[0]);
        zone.setLongitude(coordinates[1]);
        zone.setRadius(defaultRadius(zoneType));
        zone.setSpeedLimit(defaultSpeedLimit(zoneType));
        zone.setActive(true);
        zone.setAlertEnabled(true);
        zone.setSource("OPENSTREETMAP");
        zone.setSourceType(element.path("type").asText(null));
        zone.setSourceId(element.path("id").asLong(0L));
        zone.setGeometryJson(extractGeometryJson(element));
        zone.setDescription(buildDescription(tags, zoneType));
        zone.setCreatedAt(LocalDateTime.now());

        return zone;
    }

    private double[] extractCoordinates(JsonNode element) {
        if (element.has("lat") && element.has("lon")) {
            return new double[] {
                    element.path("lat").asDouble(),
                    element.path("lon").asDouble()
            };
        }

        JsonNode center = element.path("center");
        if (center.has("lat") && center.has("lon")) {
            return new double[] {
                    center.path("lat").asDouble(),
                    center.path("lon").asDouble()
            };
        }

        JsonNode geometry = element.path("geometry");
        if (geometry.isArray() && geometry.size() > 0) {
            double lat = 0;
            double lon = 0;
            int count = 0;

            for (JsonNode point : geometry) {
                if (point.has("lat") && point.has("lon")) {
                    lat += point.path("lat").asDouble();
                    lon += point.path("lon").asDouble();
                    count++;
                }
            }

            if (count > 0) {
                return new double[] { lat / count, lon / count };
            }
        }

        return null;
    }

    /*
     * Stores only [lat,lon] pairs, keeping the DB payload small.
     */
    private String extractGeometryJson(JsonNode element) {
        JsonNode geometry = element.path("geometry");
        if (!geometry.isArray() || geometry.size() < 2) {
            return null;
        }

        List<double[]> points = new ArrayList<>();

        for (JsonNode point : geometry) {
            if (point.has("lat") && point.has("lon")) {
                points.add(new double[] {
                        point.path("lat").asDouble(),
                        point.path("lon").asDouble()
                });
            }
        }

        if (points.size() < 2) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(points);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<SafetyZone> saveZones(List<SafetyZone> zones) {
        if (zones.isEmpty()) {
            return List.of();
        }

        Map<String, SafetyZone> unique = new LinkedHashMap<>();

        for (SafetyZone zone : zones) {
            String sourceKey = zone.getSource() + "|"
                    + zone.getSourceType() + "|"
                    + zone.getSourceId();

            if (zone.getSourceId() != null && zone.getSourceId() > 0) {
                unique.putIfAbsent(sourceKey, zone);
            } else {
                String coordinateKey = zone.getZoneType() + "|"
                        + round(zone.getLatitude(), 5) + "|"
                        + round(zone.getLongitude(), 5);
                unique.putIfAbsent(coordinateKey, zone);
            }
        }

        List<SafetyZone> candidates = new ArrayList<>(unique.values());
        List<SafetyZone> toSave = new ArrayList<>();

        // Batch the common OSM identity lookup instead of doing one SELECT per feature.
        Map<String, java.util.Set<Long>> existingIdsByType = new LinkedHashMap<>();
        Map<String, List<Long>> requestedIdsByType = new LinkedHashMap<>();

        for (SafetyZone zone : candidates) {
            if (zone.getSourceId() == null || zone.getSourceId() <= 0
                    || zone.getSourceType() == null || zone.getSource() == null) {
                continue;
            }
            String key = zone.getSource() + "|" + zone.getSourceType();
            requestedIdsByType.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(zone.getSourceId());
        }

        for (Map.Entry<String, List<Long>> entry : requestedIdsByType.entrySet()) {
            String[] parts = entry.getKey().split("\\|", -1);
            List<SafetyZone> existing = safetyZoneRepository
                    .findBySourceAndSourceTypeAndSourceIdIn(
                            parts[0], parts[1], entry.getValue());

            java.util.Set<Long> ids = new java.util.HashSet<>();
            for (SafetyZone zone : existing) {
                ids.add(zone.getSourceId());
            }
            existingIdsByType.put(entry.getKey(), ids);
        }

        for (SafetyZone zone : candidates) {
            if (zone.getSourceId() != null && zone.getSourceId() > 0
                    && zone.getSourceType() != null && zone.getSource() != null) {

                String key = zone.getSource() + "|" + zone.getSourceType();
                if (!existingIdsByType
                        .getOrDefault(key, java.util.Set.of())
                        .contains(zone.getSourceId())) {
                    toSave.add(zone);
                }
            } else if (!existsNearby(
                    zone.getZoneType(), zone.getLatitude(), zone.getLongitude())) {
                toSave.add(zone);
            }
        }

        if (toSave.isEmpty()) {
            return List.of();
        }

        return safetyZoneRepository.saveAll(toSave);
    }

    private boolean existsNearby(
            String zoneType, double latitude, double longitude) {

        for (SafetyZone zone :
                safetyZoneRepository.findByZoneTypeIgnoreCase(zoneType)) {

            if (zone.getLatitude() == null || zone.getLongitude() == null) {
                continue;
            }

            if (distanceInMeters(
                    latitude, longitude,
                    zone.getLatitude(), zone.getLongitude()) <= 100.0) {
                return true;
            }
        }

        return false;
    }

    public double distanceInMeters(
            double lat1, double lon1, double lat2, double lon2) {

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);

        return EARTH_RADIUS_METERS
                * 2
                * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private double defaultRadius(String zoneType) {
        return switch (zoneType) {
            case "SCHOOL" -> 300.0;
            case "WILDLIFE" -> 500.0;
            case "HILL_AREA" -> 500.0;
            default -> 300.0;
        };
    }

    private int defaultSpeedLimit(String zoneType) {
        return switch (zoneType) {
            case "SCHOOL", "WILDLIFE", "HILL_AREA" -> 30;
            default -> 40;
        };
    }

    private String buildDescription(JsonNode tags, String zoneType) {
        String sourceName = tags.path("name").asText("");
        return switch (zoneType) {
            case "SCHOOL" -> "School safety zone imported from OpenStreetMap"
                    + (sourceName.isBlank() ? "" : ": " + sourceName);
            case "WILDLIFE" -> "Wildlife / protected area imported from OpenStreetMap"
                    + (sourceName.isBlank() ? "" : ": " + sourceName);
            case "HILL_AREA" -> "Hill / mountainous feature imported from OpenStreetMap"
                    + (sourceName.isBlank() ? "" : ": " + sourceName);
            default -> "Safety zone imported from OpenStreetMap";
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "Safety Zone";
    }

    private String defaultName(String zoneType) {
        return switch (zoneType) {
            case "SCHOOL" -> "School Safety Zone";
            case "WILDLIFE" -> "Wildlife Safety Zone";
            case "HILL_AREA" -> "Hill Area";
            default -> "Safety Zone";
        };
    }

    private double round(double value, int decimals) {
        double multiplier = Math.pow(10, decimals);
        return Math.round(value * multiplier) / multiplier;
    }

    private void validateCoordinates(
            double latitude, double longitude, double radius) {

        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Invalid latitude");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid longitude");
        }
        if (radius <= 0 || radius > 50000) {
            throw new IllegalArgumentException(
                    "Radius must be between 1 and 50000 meters");
        }
    }

    private void validateLiveBbox(
            double south, double west, double north, double east) {

        if (south < -90 || south > 90
                || north < -90 || north > 90
                || west < -180 || west > 180
                || east < -180 || east > 180
                || south >= north || west >= east) {
            throw new IllegalArgumentException("Invalid bounding box");
        }

        // Public Overpass is not a whole-India POI database. Keep live
        // viewport requests small and let the DB/batch importer handle
        // persistent national coverage.
        if ((north - south) > 2.5 || (east - west) > 2.5) {
            throw new IllegalArgumentException(
                    "Zoom in to a smaller map area before loading live OSM zones.");
        }
    }

    private void validateBbox(
            double south, double west, double north, double east) {

        if (south < -90 || south > 90
                || north < -90 || north > 90
                || west < -180 || west > 180
                || east < -180 || east > 180
                || south >= north || west >= east) {
            throw new IllegalArgumentException("Invalid bounding box");
        }

        if ((north - south) > 5 || (east - west) > 5) {
            throw new IllegalArgumentException(
                    "Bounding box too large. Use regional chunks of at most 5 degrees.");
        }
    }
}
