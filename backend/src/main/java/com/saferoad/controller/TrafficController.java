package com.saferoad.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/traffic")
public class TrafficController {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${tomtom.traffic.api.key}")
    private String trafficApiKey;


    // =========================================================
    // ROUTE TRAFFIC API
    // =========================================================

    @PostMapping("/route")
    public ResponseEntity<RouteTrafficResponse> getRouteTraffic(
            @RequestBody RouteTrafficRequest request) {

        List<RouteTrafficResult> results =
                new ArrayList<>();

        if (request == null ||
                request.routes == null ||
                request.routes.isEmpty()) {

            return ResponseEntity.ok(
                    new RouteTrafficResponse(results)
            );
        }


        for (Route route : request.routes) {

            RouteTrafficResult result =
                    analyzeRoute(route);

            results.add(result);
        }


        return ResponseEntity.ok(
                new RouteTrafficResponse(results)
        );
    }


    // =========================================================
    // ANALYZE ONE ROUTE
    // =========================================================

    private RouteTrafficResult analyzeRoute(
            Route route) {

        List<TrafficPoint> trafficPoints =
                new ArrayList<>();


        List<Point> sampledPoints =
                samplePoints(
                        route.points,
                        5
                );


        double currentSpeedTotal = 0.0;
        double freeFlowSpeedTotal = 0.0;
        double congestionTotal = 0.0;

        int validPoints = 0;
        int roadClosureCount = 0;


        for (Point point : sampledPoints) {

            try {

                String url =
                        UriComponentsBuilder
                                .fromUriString(
                                        "https://api.tomtom.com/traffic/services/4/flowSegmentData/absolute/10/json"
                                )
                                .queryParam(
                                        "point",
                                        point.lat + "," + point.lon
                                )
                                .queryParam(
                                        "unit",
                                        "KMPH"
                                )
                                .queryParam(
                                        "key",
                                        trafficApiKey
                                )
                                .toUriString();


                JsonNode root =
                        restTemplate.getForObject(
                                url,
                                JsonNode.class
                        );


                JsonNode flow =
                        root == null
                                ? null
                                : root.path(
                                "flowSegmentData"
                        );


                if (flow == null ||
                        flow.isMissingNode()) {

                    continue;
                }


                double currentSpeed =
                        flow.path(
                                "currentSpeed"
                        ).asDouble(0);


                double freeFlowSpeed =
                        flow.path(
                                "freeFlowSpeed"
                        ).asDouble(0);


                double confidence =
                        flow.path(
                                "confidence"
                        ).asDouble(0);


                boolean roadClosure =
                        flow.path(
                                "roadClosure"
                        ).asBoolean(false);


                double congestionPercent = 0.0;


                if (freeFlowSpeed > 0) {

                    congestionPercent =
                            (
                                    1.0 -
                                            (
                                                    currentSpeed /
                                                            freeFlowSpeed
                                            )
                            ) * 100.0;


                    if (congestionPercent < 0) {
                        congestionPercent = 0;
                    }


                    if (congestionPercent > 100) {
                        congestionPercent = 100;
                    }
                }


                if (roadClosure) {
                    roadClosureCount++;
                }


                currentSpeedTotal +=
                        currentSpeed;

                freeFlowSpeedTotal +=
                        freeFlowSpeed;

                congestionTotal +=
                        congestionPercent;

                validPoints++;


                TrafficPoint trafficPoint =
                        new TrafficPoint();


                trafficPoint.lat =
                        point.lat;

                trafficPoint.lon =
                        point.lon;

                trafficPoint.currentSpeed =
                        currentSpeed;

                trafficPoint.freeFlowSpeed =
                        freeFlowSpeed;

                trafficPoint.congestionPercent =
                        round(congestionPercent);

                trafficPoint.confidence =
                        confidence;

                trafficPoint.roadClosure =
                        roadClosure;


                trafficPoints.add(
                        trafficPoint
                );


            } catch (Exception e) {

                System.err.println(
                        "Traffic API error at " +
                                point.lat + "," +
                                point.lon +
                                " : " +
                                e.getMessage()
                );
            }
        }


        double averageCurrentSpeed = 0.0;
        double averageFreeFlowSpeed = 0.0;
        double averageCongestion = 0.0;


        if (validPoints > 0) {

            averageCurrentSpeed =
                    currentSpeedTotal /
                            validPoints;

            averageFreeFlowSpeed =
                    freeFlowSpeedTotal /
                            validPoints;

            averageCongestion =
                    congestionTotal /
                            validPoints;
        }


        String riskLevel;


        if (
                roadClosureCount > 0 ||
                        averageCongestion >= 60
        ) {

            riskLevel = "HIGH";

        } else if (
                averageCongestion >= 30
        ) {

            riskLevel = "MEDIUM";

        } else {

            riskLevel = "LOW";
        }


        RouteTrafficResult result =
                new RouteTrafficResult();


        result.id =
                route.id;

        result.name =
                route.name;

        result.distanceKm =
                route.distanceKm;

        result.durationMin =
                route.durationMin;

        result.averageCurrentSpeed =
                round(averageCurrentSpeed);

        result.averageFreeFlowSpeed =
                round(averageFreeFlowSpeed);

        result.trafficCongestionPercent =
                round(averageCongestion);

        result.roadClosureCount =
                roadClosureCount;

        result.trafficRiskLevel =
                riskLevel;

        result.trafficPoints =
                trafficPoints;


        return result;
    }


    // =========================================================
    // SAMPLE ROUTE POINTS
    // =========================================================

    private List<Point> samplePoints(
            List<Point> points,
            int maxPoints) {

        List<Point> result =
                new ArrayList<>();


        if (
                points == null ||
                        points.isEmpty()
        ) {

            return result;
        }


        if (points.size() <= maxPoints) {

            result.addAll(points);

            return result;
        }


        int[] indexes = {

                0,

                (int) Math.floor(
                        points.size() * 0.25
                ),

                (int) Math.floor(
                        points.size() * 0.50
                ),

                (int) Math.floor(
                        points.size() * 0.75
                ),

                points.size() - 1
        };


        for (int index : indexes) {

            if (
                    index >= 0 &&
                            index < points.size()
            ) {

                result.add(
                        points.get(index)
                );
            }
        }


        return result;
    }


    // =========================================================
    // ROUND NUMBER
    // =========================================================

    private double round(double value) {

        return Math.round(
                value * 100.0
        ) / 100.0;
    }


    // =========================================================
    // REQUEST CLASSES
    // =========================================================

    public static class RouteTrafficRequest {

        public List<Route> routes;

        public RouteTrafficRequest() {
        }
    }


    public static class Route {

        public String id;

        public String name;

        public double distanceKm;

        public double durationMin;

        public List<Point> points;

        public Route() {
        }
    }


    public static class Point {

        public double lat;

        public double lon;

        public Point() {
        }

        public Point(
                double lat,
                double lon) {

            this.lat = lat;
            this.lon = lon;
        }
    }


    // =========================================================
    // RESPONSE CLASSES
    // =========================================================

    public static class RouteTrafficResponse {

        public List<RouteTrafficResult> routes;

        public RouteTrafficResponse(
                List<RouteTrafficResult> routes) {

            this.routes = routes;
        }
    }


    public static class RouteTrafficResult {

        public String id;

        public String name;

        public double distanceKm;

        public double durationMin;

        public double averageCurrentSpeed;

        public double averageFreeFlowSpeed;

        public double trafficCongestionPercent;

        public int roadClosureCount;

        public String trafficRiskLevel;

        public List<TrafficPoint> trafficPoints;

        public RouteTrafficResult() {
        }
    }


    public static class TrafficPoint {

        public double lat;

        public double lon;

        public double currentSpeed;

        public double freeFlowSpeed;

        public double congestionPercent;

        public double confidence;

        public boolean roadClosure;

        public TrafficPoint() {
        }
    }
}