package com.saferoad.controller;







import java.time.LocalDateTime;



import java.util.LinkedHashMap;



import java.util.List;



import java.util.Map;











import org.springframework.http.HttpStatus;



import org.springframework.http.ResponseEntity;



import org.springframework.web.bind.annotation.GetMapping;



import org.springframework.web.bind.annotation.PathVariable;



import org.springframework.web.bind.annotation.PostMapping;



import org.springframework.web.bind.annotation.RequestBody;



import org.springframework.web.bind.annotation.RequestMapping;



import org.springframework.web.bind.annotation.RequestParam;



import org.springframework.web.bind.annotation.RestController;







import com.saferoad.controller.dto.SafetyZoneResponse;



import com.saferoad.entity.SafetyZone;



import com.saferoad.repository.SafetyZoneRepository;



import com.saferoad.service.IndianSafetyZoneService;

import com.saferoad.service.GeoapifySafetyZoneService;







@RestController



@RequestMapping("/api/safety-zones")



public class SafetyZoneController {







    private final SafetyZoneRepository safetyZoneRepository;



    private final IndianSafetyZoneService indianSafetyZoneService;

    private final GeoapifySafetyZoneService geoapifySafetyZoneService;








    public SafetyZoneController(
            SafetyZoneRepository safetyZoneRepository,
            IndianSafetyZoneService indianSafetyZoneService,
            GeoapifySafetyZoneService geoapifySafetyZoneService) {

        this.safetyZoneRepository = safetyZoneRepository;
        this.indianSafetyZoneService = indianSafetyZoneService;
        this.geoapifySafetyZoneService = geoapifySafetyZoneService;
    }







    @PostMapping



    public ResponseEntity<?> createSafetyZone(



            @RequestBody SafetyZone safetyZone) {



        try {



            if (safetyZone.getCreatedAt() == null) {



                safetyZone.setCreatedAt(LocalDateTime.now());



            }



            if (safetyZone.getActive() == null) {



                safetyZone.setActive(true);



            }



            if (safetyZone.getAlertEnabled() == null) {



                safetyZone.setAlertEnabled(true);



            }







            SafetyZone saved = safetyZoneRepository.save(safetyZone);



            return ResponseEntity.status(HttpStatus.CREATED)



                    .body(SafetyZoneResponse.fromEntity(saved));



        } catch (Exception ex) {



            return ResponseEntity.status(HttpStatus.BAD_REQUEST)



                    .body(ex.getMessage());



        }



    }







    @GetMapping



    public ResponseEntity<List<SafetyZoneResponse>> getActiveSafetyZones() {



        return ResponseEntity.ok(



                safetyZoneRepository.findByActiveTrue()



                        .stream()



                        .map(SafetyZoneResponse::fromEntity)



                        .toList());



    }







    @GetMapping("/{id}")



    public ResponseEntity<?> getSafetyZoneById(@PathVariable Long id) {



        return safetyZoneRepository.findById(id)



                .map(zone -> ResponseEntity.ok(



                        SafetyZoneResponse.fromEntity(zone)))



                .orElseGet(() -> ResponseEntity.notFound().build());



    }







    @GetMapping("/type/{zoneType}")



    public ResponseEntity<List<SafetyZoneResponse>> getZonesByType(



            @PathVariable String zoneType) {



        return ResponseEntity.ok(



                safetyZoneRepository.findByZoneTypeIgnoreCaseAndActiveTrue(zoneType)



                        .stream()



                        .map(SafetyZoneResponse::fromEntity)



                        .toList());



    }







    /*



     * IMPORTANT:



     * This endpoint is used by the GPS alert engine.



     * It does NOT care whether a frontend layer is visible.



     */



    @GetMapping("/bbox")



    public ResponseEntity<?> getZonesInBoundingBox(



            @RequestParam double south,



            @RequestParam double west,



            @RequestParam double north,



            @RequestParam double east,



            @RequestParam(required = false) String zoneType) {







        if (south < -90 || north > 90 || west < -180 || east > 180



                || south >= north || west >= east) {



            return ResponseEntity.badRequest().body("Invalid bounding box");



        }







        List<SafetyZoneResponse> zones = safetyZoneRepository.findByActiveTrue()



                .stream()



                .filter(zone -> zone.getLatitude() >= south



                        && zone.getLatitude() <= north



                        && zone.getLongitude() >= west



                        && zone.getLongitude() <= east)



                .filter(zone -> zoneType == null



                        || zoneType.isBlank()



                        || zone.getZoneType().equalsIgnoreCase(zoneType))



                .map(SafetyZoneResponse::fromEntity)



                .toList();







        return ResponseEntity.ok(zones);



    }







    /**



     * Live OSM data for the currently visible map area.



     * This is display-only: it does not change the database.



     * Persistent safety/alert data remains database-backed.



     */



    @GetMapping("/live")



    public ResponseEntity<?> getLiveOsmZones(



            @RequestParam double south,



            @RequestParam double west,



            @RequestParam double north,



            @RequestParam double east,



            @RequestParam String zoneType) {



        try {



            return ResponseEntity.ok(



                    indianSafetyZoneService.fetchLiveByBoundingBox(



                            south, west, north, east, zoneType));



        } catch (Exception ex) {



            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)



                    .body("Live OSM request failed: " + ex.getMessage());



        }



    }







    /**

     * Live Geoapify safety locations for the currently visible map area.

     * This endpoint is display-only and does not write to PostgreSQL.

     */

    @GetMapping("/geoapify")

    public ResponseEntity<?> getLiveGeoapifyZones(

            @RequestParam double south,

            @RequestParam double west,

            @RequestParam double north,

            @RequestParam double east,

            @RequestParam String zoneType) {



        try {

            return ResponseEntity.ok(

                    geoapifySafetyZoneService.fetchLiveByBoundingBox(

                            south, west, north, east, zoneType));

        } catch (IllegalArgumentException ex) {

            return ResponseEntity.badRequest().body(ex.getMessage());

        } catch (Exception ex) {

            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)

                    .body("Geoapify request failed: " + ex.getMessage());

        }

    }



    @GetMapping("/nearby")



    public ResponseEntity<?> getNearbySafetyZones(



            @RequestParam double latitude,



            @RequestParam double longitude,



            @RequestParam(defaultValue = "10000") double radius,



            @RequestParam(required = false) String zoneType) {







        if (latitude < -90 || latitude > 90



                || longitude < -180 || longitude > 180



                || radius <= 0 || radius > 50000) {



            return ResponseEntity.badRequest()



                    .body("Invalid latitude, longitude or radius");



        }







        List<SafetyZone> zones = safetyZoneRepository.findByActiveTrue();







        List<Map<String, Object>> result = zones.stream()



                .filter(zone -> zone.getAlertEnabled() == null



                        || zone.getAlertEnabled())



                .filter(zone -> zoneType == null



                        || zoneType.isBlank()



                        || zone.getZoneType().equalsIgnoreCase(zoneType))



                .map(zone -> {



                    double distance = indianSafetyZoneService.distanceInMeters(



                            latitude,



                            longitude,



                            zone.getLatitude(),



                            zone.getLongitude());







                    if (distance > radius) {



                        return null;



                    }







                    Map<String, Object> item = new LinkedHashMap<>();



                    item.put("zone", SafetyZoneResponse.fromEntity(zone));



                    item.put("distanceMeters", Math.round(distance * 10.0) / 10.0);



                    item.put("distanceKm",



                            Math.round((distance / 1000.0) * 100.0) / 100.0);



                    return item;



                })



                .filter(item -> item != null)



                .sorted((a, b) -> Double.compare(



                        ((Number) a.get("distanceMeters")).doubleValue(),



                        ((Number) b.get("distanceMeters")).doubleValue()))



                .toList();







        return ResponseEntity.ok(result);



    }







    /*



     * Regional OSM sync. Keep boxes small; do not query all Indian schools



     * through one giant public Overpass request.



     */



    @PostMapping("/import/bbox")



    public ResponseEntity<?> importBoundingBox(



            @RequestParam double south,



            @RequestParam double west,



            @RequestParam double north,



            @RequestParam double east,



            @RequestParam String zoneType) {







        try {



            return ResponseEntity.ok(



                    indianSafetyZoneService.fetchAndSaveByBoundingBox(



                            south, west, north, east, zoneType));



        } catch (Exception ex) {



            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)



                    .body("OSM bounding-box import failed: " + ex.getMessage());



        }



    }







    /**



     * Local PBF preload endpoint. The importer sends GeoJSON features in batches.



     * No Overpass request is made here.



     */



    @PostMapping("/import/geojson-batch")



    public ResponseEntity<?> importGeoJsonBatch(@RequestBody com.fasterxml.jackson.databind.JsonNode features) {



        try {



            return ResponseEntity.ok(



                    indianSafetyZoneService.importGeoJsonBatch(features));



        } catch (Exception ex) {



            return ResponseEntity.status(HttpStatus.BAD_REQUEST)



                    .body("Local OSM batch import failed: " + ex.getMessage());



        }



    }







    @PostMapping("/import")



    public ResponseEntity<?> importIndianSafetyZones(



            @RequestParam double latitude,



            @RequestParam double longitude,



            @RequestParam(defaultValue = "10000") double radius) {



        try {



            return ResponseEntity.ok(



                    indianSafetyZoneService.fetchAndSaveIndianSafetyZones(



                            latitude, longitude, radius));



        } catch (Exception ex) {



            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)



                    .body("Indian safety data import failed: " + ex.getMessage());



        }



    }







    @PostMapping("/import/schools")



    public ResponseEntity<?> importSchoolZones(



            @RequestParam double latitude,



            @RequestParam double longitude,



            @RequestParam(defaultValue = "10000") double radius) {



        try {



            return ResponseEntity.ok(



                    indianSafetyZoneService.fetchAndSaveSchoolZones(



                            latitude, longitude, radius));



        } catch (Exception ex) {



            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)



                    .body("Indian school data import failed: " + ex.getMessage());



        }



    }







    @PostMapping("/import/hills")



    public ResponseEntity<?> importHillZones(



            @RequestParam double latitude,



            @RequestParam double longitude,



            @RequestParam(defaultValue = "10000") double radius) {



        try {



            return ResponseEntity.ok(



                    indianSafetyZoneService.fetchAndSaveHillZones(



                            latitude, longitude, radius));



        } catch (Exception ex) {



            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)



                    .body("Indian hill data import failed: " + ex.getMessage());



        }



    }







    @PostMapping("/import/wildlife")



    public ResponseEntity<?> importWildlifeZones(



            @RequestParam double latitude,



            @RequestParam double longitude,



            @RequestParam(defaultValue = "10000") double radius) {



        try {



            return ResponseEntity.ok(



                    indianSafetyZoneService.fetchAndSaveWildlifeZones(



                            latitude, longitude, radius));



        } catch (Exception ex) {



            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)



                    .body("Indian wildlife data import failed: " + ex.getMessage());



        }



    }



}
