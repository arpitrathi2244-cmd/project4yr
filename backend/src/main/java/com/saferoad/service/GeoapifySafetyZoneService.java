package com.saferoad.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GeoapifySafetyZoneService {

    private static final String GEOAPIFY_URL =
            "https://api.geoapify.com/v2/places";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${geoapify.api.key:}")
    private String apiKey;

    public GeoapifySafetyZoneService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public List<Map<String, Object>> fetchLiveByBoundingBox(
            double south, double west, double north, double east,
            String zoneType) {

        validateBbox(south, west, north, east);

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEOAPIFY_API_KEY is not configured.");
        }

        String type = normalizeType(zoneType);

        String categories = switch (type) {
            case "SCHOOL" -> "education.school";
            case "HILL_AREA" -> String.join(",",
                    "natural.mountain.hill",
                    "natural.mountain.peak",
                    "natural.mountain.rock",
                    "natural.mountain.volcano");
            case "WILDLIFE" -> String.join(",",
                    "national_park",
                    "natural.protected_area");
            default -> throw new IllegalArgumentException(
                    "zoneType must be SCHOOL, HILL_AREA or WILDLIFE");
        };

        String filter = String.format(
                "rect:%f,%f,%f,%f", west, south, east, north);

        // Fetch every available page for this focused route-corridor tile.
        final int pageSize = 500;

        Map<String, Map<String, Object>> uniqueResults =
                new LinkedHashMap<>();

        try {
            for (int page = 0; ; page++) {

                int offset = page * pageSize;

                String url = GEOAPIFY_URL
                        + "?categories=" + encode(categories)
                        + "&filter=" + encode(filter)
                        + "&limit=" + pageSize
                        + "&offset=" + offset
                        + "&lang=en"
                        + "&apiKey=" + encode(apiKey);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "application/json")
                        .header("User-Agent", "SafeRoad/1.0")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException(
                            "Geoapify returned HTTP " + response.statusCode());
                }

                JsonNode features = objectMapper.readTree(response.body())
                        .path("features");

                if (!features.isArray() || features.size() == 0) {
                    break;
                }

                for (JsonNode feature : features) {
                    JsonNode properties = feature.path("properties");
                    JsonNode geometry = feature.path("geometry");
                    JsonNode coordinates = geometry.path("coordinates");

                    if (!coordinates.isArray() || coordinates.size() < 2) {
                        continue;
                    }

                    double longitude = coordinates.get(0).asDouble();
                    double latitude = coordinates.get(1).asDouble();

                    if (!isInsideIndia(latitude, longitude)) {
                        continue;
                    }

                    String placeId = properties.path("place_id").asText("");
                    String stableId = placeId.isBlank()
                            ? String.format("%.7f:%.7f", latitude, longitude)
                            : placeId;

                    String name = firstNonBlank(
                            properties.path("name").asText(""),
                            properties.path("formatted").asText(""),
                            defaultName(type));

                    String address = properties.path("formatted").asText("");

                    Map<String, Object> zone = new LinkedHashMap<>();
                    zone.put("id", "geoapify:" + stableId);
                    zone.put("name", name);
                    zone.put("zoneType", type);
                    zone.put("location", firstNonBlank(
                            properties.path("city").asText(""),
                            properties.path("state").asText(""),
                            "India"));
                    zone.put("latitude", latitude);
                    zone.put("longitude", longitude);
                    zone.put("radius", defaultRadius(type));
                    zone.put("speedLimit", 30);
                    zone.put("active", true);
                    zone.put("alertEnabled", true);
                    zone.put("source", "GEOAPIFY");
                    zone.put("sourceType", "PLACE");
                    zone.put("sourceId", placeId);
                    zone.put("description", address.isBlank()
                            ? "Live Geoapify location" : address);
                    zone.put("geometryJson", geometry.toString());

                    // Deduplicate the same place across pages.
                    uniqueResults.putIfAbsent(stableId, zone);
                }

                // A short page means this is the final page.
                if (features.size() < pageSize) {
                    break;
                }
            }

            return new ArrayList<>(uniqueResults.values());

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Geoapify request interrupted.", ex);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Geoapify request failed: " + ex.getMessage(), ex);
        }
    }

    private String normalizeType(String zoneType) {
        if (zoneType == null || zoneType.isBlank()) {
            throw new IllegalArgumentException("zoneType is required");
        }
        return zoneType.trim().toUpperCase();
    }

    private void validateBbox(
            double south, double west, double north, double east) {
        if (south < -90 || north > 90 || west < -180 || east > 180
                || south >= north || west >= east) {
            throw new IllegalArgumentException("Invalid bounding box");
        }

        if ((north - south) > 2.5 || (east - west) > 2.5) {
            throw new IllegalArgumentException(
                    "Zoom in to a smaller map area before loading live safety locations.");
        }
    }

    private boolean isInsideIndia(double latitude, double longitude) {
        return latitude >= 6.0 && latitude <= 37.5
                && longitude >= 68.0 && longitude <= 97.5;
    }

    private double defaultRadius(String type) {
        return switch (type) {
            case "SCHOOL" -> 300.0;
            case "WILDLIFE" -> 500.0;
            case "HILL_AREA" -> 500.0;
            default -> 300.0;
        };
    }

    private String defaultName(String type) {
        return switch (type) {
            case "SCHOOL" -> "School Safety Zone";
            case "WILDLIFE" -> "Wildlife / Protected Area";
            case "HILL_AREA" -> "Hill / Mountain Area";
            default -> "Safety Location";
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "Safety Location";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
