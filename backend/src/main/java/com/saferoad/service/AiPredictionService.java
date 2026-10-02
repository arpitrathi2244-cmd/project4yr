package com.saferoad.service;

import com.saferoad.controller.dto.AiPredictionResponse;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class AiPredictionService {

    private final RestTemplate restTemplate = new RestTemplate();

    private final String AI_API_URL =
            "http://localhost:5000/predict";

    public AiPredictionResponse predict(Map<String, Object> userData) {

        Map<String, Object> modelData = new HashMap<>();

        // ==========================================
        // USER INPUTS
        // ==========================================

        String vehicleType =
                String.valueOf(userData.get("vehicleType"));

        String ageGroup =
                String.valueOf(userData.get("ageGroup"));

        String experience =
                String.valueOf(userData.get("experience"));

        String roadCondition =
                String.valueOf(userData.get("roadCondition"));

        String traffic =
                String.valueOf(userData.get("traffic"));

        String weather =
                String.valueOf(userData.get("weather"));


        // ==========================================
        // 1. TIME
        // ==========================================

        modelData.put("Time", "18:00");


        // ==========================================
        // 2. DAY
        // ==========================================

        modelData.put("Day_of_week", "Monday");


        // ==========================================
        // 3. AGE
        // ==========================================

        modelData.put("Age_band_of_driver", ageGroup);


        // ==========================================
        // 4. SEX
        // ==========================================

        // Currently default.
        // Later we can take this from user profile.
        modelData.put("Sex_of_driver", "Male");


        // ==========================================
        // 5. DRIVING EXPERIENCE
        // ==========================================

        modelData.put("Driving_experience", experience);


        // ==========================================
        // 6. VEHICLE TYPE
        // ==========================================

        modelData.put("Type_of_vehicle", vehicleType);


        // ==========================================
        // 7. VEHICLE SERVICE YEAR
        // ==========================================

        modelData.put(
                "Service_year_of_vehicle",
                "5-10yr"
        );


        // ==========================================
        // 8. VEHICLE DEFECT
        // ==========================================

        modelData.put(
                "Defect_of_vehicle",
                "No defect"
        );


        // ==========================================
        // 9. ACCIDENT AREA
        // ==========================================

        modelData.put(
                "Area_accident_occured",
                "Urban areas"
        );


        // ==========================================
        // 10. LANES / MEDIAN
        // ==========================================

        modelData.put(
                "Lanes_or_Medians",
                "Two-way (divided with broken lines)"
        );


        // ==========================================
        // 11. ROAD ALIGNMENT
        // ==========================================

        modelData.put(
                "Road_allignment",
                "Tangent road with flat terrain"
        );


        // ==========================================
        // 12. JUNCTION
        // ==========================================

        modelData.put(
                "Types_of_Junction",
                "No junction"
        );


        // ==========================================
        // 13. ROAD SURFACE
        // ==========================================

        modelData.put(
                "Road_surface_type",
                "Asphalt roads"
        );


        // ==========================================
        // 14. ROAD SURFACE CONDITION
        // ==========================================

        modelData.put(
                "Road_surface_conditions",
                roadCondition
        );


        // ==========================================
        // 15. LIGHT CONDITIONS
        // ==========================================

        modelData.put(
                "Light_conditions",
                "Daylight"
        );


        // ==========================================
        // 16. WEATHER
        // ==========================================

        modelData.put(
                "Weather_conditions",
                weather
        );


        // ==========================================
        // 17. COLLISION TYPE
        // ==========================================

        modelData.put(
                "Type_of_collision",
                "Vehicle with vehicle collision"
        );


        // ==========================================
        // 18. VEHICLES INVOLVED
        // ==========================================

        modelData.put(
                "Number_of_vehicles_involved",
                2
        );


        // ==========================================
        // 19. VEHICLE MOVEMENT
        // ==========================================

        modelData.put(
                "Vehicle_movement",
                "Going straight"
        );


        // ==========================================
        // 20. CAUSE
        // ==========================================

        modelData.put(
                "Cause_of_accident",
                "No distancing"
        );


        // ==========================================
        // TRAFFIC
        // ==========================================
        //
        // IMPORTANT:
        // Your current 20-feature model does not have
        // a "Traffic" feature.
        //
        // So traffic is currently collected from the
        // frontend but is NOT directly sent to the model.
        //
        // Later we can use traffic to derive some
        // road-risk information or retrain the model.
        // ==========================================


        // ==========================================
        // SEND TO FLASK AI
        // ==========================================

        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(
                        modelData,
                        headers
                );


        ResponseEntity<AiPredictionResponse> response =
                restTemplate.exchange(
                        AI_API_URL,
                        HttpMethod.POST,
                        request,
                        AiPredictionResponse.class
                );


        return response.getBody();
    }
}