package com.saferoad.controller;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class SafeRouteAIController {

    private static final String PYTHON_AI_URL =
            "http://127.0.0.1:5001/predict-safe-route";

    private final RestClient restClient = RestClient.builder().build();

    @PostMapping(
            value = "/safe-route",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> predictSafeRoute(
            @RequestBody Map<String, Object> requestBody
    ) {
        try {
            ResponseEntity<String> pythonResponse = restClient.post()
                    .uri(PYTHON_AI_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toEntity(String.class);

            return ResponseEntity
                    .status(pythonResponse.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(pythonResponse.getBody());

        } catch (RestClientResponseException error) {
            String body = error.getResponseBodyAsString();
            if (body == null || body.isBlank()) {
                body = "{\"status\":\"error\",\"error\":\"Python AI service returned an error.\"}";
            }

            return ResponseEntity
                    .status(error.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);

        } catch (Exception error) {
            String message = error.getMessage();
            if (message == null || message.isBlank()) {
                message = "Unable to connect to Python AI service on port 5001.";
            }

            String safeMessage = message
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\r", " ")
                    .replace("\n", " ");

            return ResponseEntity
                    .internalServerError()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                            "{\"status\":\"error\",\"error\":\""
                                    + safeMessage
                                    + "\"}"
                    );
        }
    }
}
