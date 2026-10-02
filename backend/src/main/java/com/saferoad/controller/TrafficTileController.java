package com.saferoad.controller;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/traffic")
public class TrafficTileController {

    @Value("${tomtom.traffic.api.key}")
    private String tomtomApiKey;

    private final RestClient restClient = RestClient.create();

    @GetMapping(
            value = "/tile/{z}/{x}/{y}.png",
            produces = MediaType.IMAGE_PNG_VALUE
    )
    public ResponseEntity<byte[]> getTrafficTile(
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y) {

        try {

            String url =
                    "https://api.tomtom.com/traffic/map/4/tile/flow/"
                            + "relative0/"
                            + z + "/"
                            + x + "/"
                            + y
                            + ".png?key="
                            + tomtomApiKey
                            + "&tileSize=256";

            byte[] image = restClient
                    .get()
                    .uri(URI.create(url))
                    .accept(MediaType.IMAGE_PNG)
                    .retrieve()
                    .body(byte[].class);

            if (image == null || image.length == 0) {
                System.out.println(
                        "TomTom returned empty traffic tile: "
                                + z + "/" + x + "/" + y
                );

                return ResponseEntity
                        .status(HttpStatus.NO_CONTENT)
                        .build();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setCacheControl("max-age=30");

            return new ResponseEntity<>(
                    image,
                    headers,
                    HttpStatus.OK
            );

        } catch (Exception e) {

            System.out.println(
                    "Traffic tile error for "
                            + z + "/" + x + "/" + y
                            + " : "
                            + e.getMessage()
            );

            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .build();
        }
    }
}