package com.saferoad.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/weather")
@CrossOrigin(origins = "*")
public class WeatherController {

    @Value("${openweather.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==========================================================
    // CURRENT POINT WEATHER
    // ==========================================================

    @GetMapping
    public String getWeather(
            @RequestParam double lat,
            @RequestParam double lon) {

        return fetchWeatherJson(lat, lon);
    }


    // ==========================================================
    // WEATHER FOR ALL ROUTES
    // ==========================================================

    @PostMapping("/route")
    public ResponseEntity<RouteWeatherResponse> getRouteWeather(
            @RequestBody RouteWeatherRequest request) {

        if (request == null ||
                request.routes() == null ||
                request.routes().isEmpty()) {

            return ResponseEntity.badRequest().build();
        }


        List<RouteWeatherResult> results =
                new ArrayList<>();


        // ------------------------------------------------------
        // Process every route
        // ------------------------------------------------------

        for (RouteData route : request.routes()) {

            if (route.points() == null ||
                    route.points().isEmpty()) {

                continue;
            }


            // Maximum 5 weather locations per route
            List<Point> sampledPoints =
                    samplePoints(
                            route.points(),
                            5
                    );


            List<WeatherPoint> weatherPoints =
                    new ArrayList<>();


            // --------------------------------------------------
            // Get weather for each route point
            // --------------------------------------------------

            for (Point point : sampledPoints) {

                try {

                    String weatherJson =
                            fetchWeatherJson(
                                    point.lat(),
                                    point.lon()
                            );


                    JsonNode root =
                            objectMapper.readTree(
                                    weatherJson
                            );


                    String condition =
                            getText(
                                    root,
                                    "/weather/0/main",
                                    "Unknown"
                            );


                    String description =
                            getText(
                                    root,
                                    "/weather/0/description",
                                    "Unavailable"
                            );


                    double temperature =
                            getDouble(
                                    root,
                                    "/main/temp",
                                    0
                            );


                    int humidity =
                            getInt(
                                    root,
                                    "/main/humidity",
                                    0
                            );


                    double windSpeed =
                            getDouble(
                                    root,
                                    "/wind/speed",
                                    0
                            );


                    double rain1h =
                            getDouble(
                                    root,
                                    "/rain/1h",
                                    0
                            );


                    weatherPoints.add(
                            new WeatherPoint(
                                    point.lat(),
                                    point.lon(),
                                    condition,
                                    description,
                                    temperature,
                                    humidity,
                                    windSpeed,
                                    rain1h
                            )
                    );


                } catch (Exception e) {

                    System.out.println(
                            "Weather error for point: " +
                                    point.lat() +
                                    ", " +
                                    point.lon()
                    );

                    weatherPoints.add(
                            new WeatherPoint(
                                    point.lat(),
                                    point.lon(),
                                    "Unavailable",
                                    "Weather unavailable",
                                    0,
                                    0,
                                    0,
                                    0
                            )
                    );
                }
            }


            // --------------------------------------------------
            // Calculate route weather risk
            // --------------------------------------------------

            WeatherSummary summary =
                    calculateWeatherSummary(
                            weatherPoints
                    );


            results.add(
                    new RouteWeatherResult(

                            route.id(),

                            route.name(),

                            route.distanceKm(),

                            route.durationMin(),

                            weatherPoints,

                            summary.riskLevel(),

                            summary.riskScore()
                    )
            );
        }


        return ResponseEntity.ok(
                new RouteWeatherResponse(
                        results
                )
        );
    }


    // ==========================================================
    // OPENWEATHER REQUEST
    // ==========================================================

    private String fetchWeatherJson(
            double lat,
            double lon) {

        String url =
                UriComponentsBuilder
                        .fromUriString(
                                "https://api.openweathermap.org/data/2.5/weather"
                        )
                        .queryParam(
                                "lat",
                                lat
                        )
                        .queryParam(
                                "lon",
                                lon
                        )
                        .queryParam(
                                "appid",
                                apiKey
                        )
                        .queryParam(
                                "units",
                                "metric"
                        )
                        .toUriString();


        return restTemplate.getForObject(
                url,
                String.class
        );
    }


    // ==========================================================
    // SAMPLE ROUTE POINTS
    // ==========================================================

    private List<Point> samplePoints(
            List<Point> points,
            int maxPoints) {

        if (points.size() <= maxPoints) {

            return points;
        }


        List<Point> sampled =
                new ArrayList<>();


        int[] indexes = {

                0,

                points.size() / 4,

                points.size() / 2,

                (points.size() * 3) / 4,

                points.size() - 1
        };


        for (int index : indexes) {

            Point point =
                    points.get(index);


            if (!sampled.contains(point)) {

                sampled.add(point);
            }
        }


        return sampled;
    }


    // ==========================================================
    // WEATHER RISK
    // ==========================================================

    private WeatherSummary calculateWeatherSummary(
            List<WeatherPoint> points) {

        if (points == null ||
                points.isEmpty()) {

            return new WeatherSummary(
                    "LOW",
                    0
            );
        }


        double totalScore = 0;

        double maximumScore = 0;


        for (WeatherPoint point : points) {

            double score =
                    getWeatherScore(
                            point.condition()
                    );


            totalScore += score;


            maximumScore =
                    Math.max(
                            maximumScore,
                            score
                    );


            // Additional rain impact
            if (point.rain1h() > 0) {

                double rainScore =
                        Math.min(
                                point.rain1h() / 10.0,
                                1.0
                        );


                maximumScore =
                        Math.max(
                                maximumScore,
                                rainScore
                        );
            }
        }


        double averageScore =
                totalScore /
                        points.size();


        double finalScore =
                Math.max(
                        averageScore,
                        maximumScore
                );


        String riskLevel;


        if (finalScore >= 0.70) {

            riskLevel = "HIGH";

        } else if (finalScore >= 0.35) {

            riskLevel = "MEDIUM";

        } else {

            riskLevel = "LOW";
        }


        return new WeatherSummary(
                riskLevel,
                finalScore
        );
    }


    // ==========================================================
    // WEATHER CONDITION SCORE
    // ==========================================================

    private double getWeatherScore(
            String condition) {

        if (condition == null) {

            return 0.0;
        }


        return switch (
                condition.toLowerCase()
                ) {

            case "thunderstorm" ->
                    1.0;

            case "snow" ->
                    0.90;

            case "rain",
                 "drizzle" ->
                    0.70;

            case "mist",
                 "smoke",
                 "haze",
                 "fog",
                 "dust",
                 "sand",
                 "ash",
                 "squall",
                 "tornado" ->
                    0.60;

            case "clouds" ->
                    0.20;

            default ->
                    0.0;
        };
    }


    // ==========================================================
    // JSON HELPERS
    // ==========================================================

    private String getText(
            JsonNode root,
            String path,
            String defaultValue) {

        JsonNode node =
                root.at(path);


        if (node.isMissingNode()) {

            return defaultValue;
        }


        return node.asText(
                defaultValue
        );
    }


    private double getDouble(
            JsonNode root,
            String path,
            double defaultValue) {

        JsonNode node =
                root.at(path);


        if (node.isMissingNode()) {

            return defaultValue;
        }


        return node.asDouble(
                defaultValue
        );
    }


    private int getInt(
            JsonNode root,
            String path,
            int defaultValue) {

        JsonNode node =
                root.at(path);


        if (node.isMissingNode()) {

            return defaultValue;
        }


        return node.asInt(
                defaultValue
        );
    }


    // ==========================================================
    // DATA RECORDS
    // ==========================================================

    public record Point(
            double lat,
            double lon
    ) {}


    public record RouteData(

            String id,

            String name,

            double distanceKm,

            double durationMin,

            List<Point> points

    ) {}


    public record RouteWeatherRequest(

            List<RouteData> routes

    ) {}


    public record WeatherPoint(

            double lat,

            double lon,

            String condition,

            String description,

            double temperature,

            int humidity,

            double windSpeed,

            double rain1h

    ) {}


    public record RouteWeatherResult(

            String id,

            String name,

            double distanceKm,

            double durationMin,

            List<WeatherPoint> weatherPoints,

            String weatherRiskLevel,

            double weatherRiskScore

    ) {}


    public record RouteWeatherResponse(

            List<RouteWeatherResult> routes

    ) {}


    public record WeatherSummary(

            String riskLevel,

            double riskScore

    ) {}
}