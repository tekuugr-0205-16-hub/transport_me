package com.mobilityos.route.navigation.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.mobilityos.route.navigation.NavigationProvider;
import com.mobilityos.route.navigation.dto.NavigationOption;
import com.mobilityos.route.navigation.dto.NavigationRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GoogleNavigationProvider
        implements NavigationProvider {

    private static final String COMPUTE_ROUTES_PATH =
            "/directions/v2:computeRoutes";

    /*
     * Ask Google only for fields MobilityOS currently needs.
     *
     * This keeps the response smaller and avoids requesting
     * unnecessary route data.
     */
    private static final String FIELD_MASK =
            "routes.routeToken,"
                    + "routes.routeLabels,"
                    + "routes.distanceMeters,"
                    + "routes.duration,"
                    + "routes.staticDuration,"
                    + "routes.polyline.encodedPolyline";

    private final GoogleRoutesProperties properties;
    private final RestClient restClient;
    public GoogleNavigationProvider(
            GoogleRoutesProperties properties
    ) {
        this.properties = properties;

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    @Override
    public List<NavigationOption> calculateRoutes(
            NavigationRequest request
    ) {
        requireConfigured();

        Map<String, Object> requestBody =
                buildRequestBody(request);

        JsonNode response;

        try {
            response = restClient
                    .post()
                    .uri(COMPUTE_ROUTES_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(
                            "X-Goog-Api-Key",
                            properties.getApiKey()
                    )
                    .header(
                            "X-Goog-FieldMask",
                            FIELD_MASK
                    )
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

        } catch (RestClientResponseException exception) {

            throw new IllegalStateException(
                    "Google Routes API request failed "
                            + "with status "
                            + exception.getStatusCode(),
                    exception
            );
        }

        if (response == null) {
            throw new IllegalStateException(
                    "Google Routes API returned an empty response"
            );
        }

        JsonNode routes =
                response.path("routes");

        if (!routes.isArray()
                || routes.isEmpty()) {

            return List.of();
        }

        List<NavigationOption> options =
                new ArrayList<>();

        for (JsonNode route : routes) {

            options.add(
                    toNavigationOption(route)
            );
        }

        return List.copyOf(options);
    }

    // =========================================================
    // GOOGLE REQUEST
    // =========================================================

    private Map<String, Object> buildRequestBody(
            NavigationRequest request
    ) {
        Map<String, Object> origin =
                waypoint(
                        request.originLatitude(),
                        request.originLongitude()
                );

        Map<String, Object> destination =
                waypoint(
                        request.destinationLatitude(),
                        request.destinationLongitude()
                );

        return Map.of(
                "origin", origin,
                "destination", destination,
                "travelMode", "DRIVE",
                "routingPreference",
                request.routingPreference().name(),
                "computeAlternativeRoutes",
                request.requestAlternatives(),
                "languageCode", "en-US",
                "units", "METRIC"
        );
    }

    private Map<String, Object> waypoint(
            double latitude,
            double longitude
    ) {
        return Map.of(
                "location",
                Map.of(
                        "latLng",
                        Map.of(
                                "latitude", latitude,
                                "longitude", longitude
                        )
                )
        );
    }

    // =========================================================
    // GOOGLE RESPONSE
    // =========================================================

    private NavigationOption toNavigationOption(
            JsonNode route
    ) {
        String routeToken =
                textOrNull(
                        route.path("routeToken")
                );

        String encodedPolyline =
                textOrNull(
                        route.path("polyline")
                                .path("encodedPolyline")
                );

        Long distanceMeters =
                longOrNull(
                        route.path("distanceMeters")
                );

        Long durationSeconds =
                parseDurationSeconds(
                        route.path("duration")
                );

        Long staticDurationSeconds =
                parseDurationSeconds(
                        route.path("staticDuration")
                );
        boolean recommendedByProvider =
                hasDefaultRouteLabel(
                        route
                );

        return new NavigationOption(
                routeToken,
                encodedPolyline,
                distanceMeters,
                durationSeconds,
                staticDurationSeconds,
                recommendedByProvider
        );
    }



    private boolean hasDefaultRouteLabel(
            JsonNode route
    ) {
        JsonNode labels =
                route.path("routeLabels");

        if (!labels.isArray()) {
            return false;
        }

        for (JsonNode label : labels) {
            if ("DEFAULT_ROUTE".equals(
                    label.asText()
            )) {
                return true;
            }
        }

        return false;
    }
    // =========================================================
    // GOOGLE VALUE PARSING
    // =========================================================

    private Long parseDurationSeconds(
            JsonNode durationNode
    ) {
        String value =
                textOrNull(durationNode);

        if (value == null
                || !value.endsWith("s")) {
            return null;
        }

        String numericPart =
                value.substring(
                        0,
                        value.length() - 1
                );

        try {
            BigDecimal seconds =
                    new BigDecimal(numericPart);

            /*
             * Google durations may contain fractional seconds.
             * MobilityOS exposes whole seconds, rounding upward
             * so the displayed ETA is not understated.
             */
            return seconds
                    .setScale(
                            0,
                            RoundingMode.CEILING
                    )
                    .longValueExact();

        } catch (NumberFormatException
                 | ArithmeticException exception) {

            throw new IllegalStateException(
                    "Google Routes API returned an invalid duration",
                    exception
            );
        }
    }

    private Long longOrNull(
            JsonNode node
    ) {
        if (node == null
                || node.isMissingNode()
                || node.isNull()
                || !node.isNumber()) {

            return null;
        }

        return node.longValue();
    }

    private String textOrNull(
            JsonNode node
    ) {
        if (node == null
                || node.isMissingNode()
                || node.isNull()
                || !node.isTextual()) {

            return null;
        }

        String value =
                node.asText();

        return value.isBlank()
                ? null
                : value;
    }

    // =========================================================
    // CONFIGURATION
    // =========================================================

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "Google Routes API is not configured"
            );
        }
    }
}