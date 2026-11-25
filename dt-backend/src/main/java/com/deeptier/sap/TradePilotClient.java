package com.deeptier.sap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class TradePilotClient {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String apiKey;
    private final int timeoutSeconds;

    public TradePilotClient(ObjectMapper mapper,
                            @Value("${DEEPTIER_PYTHON_URL:http://127.0.0.1:8000}") String baseUrl,
                            @Value("${TRADEPILOT_API_KEY:}") String apiKey,
                            @Value("${DEEPTIER_TRADEPILOT_TIMEOUT_SECONDS:30}") int timeoutSeconds) {
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeoutSeconds = Math.max(1, Math.min(120, timeoutSeconds));
    }

    public ObjectNode analyze(ObjectNode shipment) {
        var request = buildRequest(shipment);
        var pending = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            var response = pending.get(timeoutSeconds, TimeUnit.SECONDS);
            return parseResponse(response);
        } catch (TimeoutException ex) {
            pending.cancel(true);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "TradePilot did not complete in time.");
        } catch (InterruptedException ex) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TradePilot request was interrupted.");
        } catch (ExecutionException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TradePilot could not be reached.");
        }
    }

    private HttpRequest buildRequest(ObjectNode shipment) {
        try {
            URI base = URI.create(baseUrl);
            if (apiKey.isBlank() || base.getHost() == null || base.getUserInfo() != null
                    || base.getQuery() != null || base.getFragment() != null
                    || !("http".equals(base.getScheme()) || "https".equals(base.getScheme()))) {
                throw new IllegalArgumentException();
            }
            URI endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/api/v1/shipments/analyze");
            return HttpRequest.newBuilder(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(shipment.toString()))
                    .build();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TradePilot connection is not configured.");
        }
    }

    private ObjectNode parseResponse(HttpResponse<String> response) {
        switch (response.statusCode()) {
            case 400, 422 -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "TradePilot rejected the shipment. Check the request against the shipment schema.");
            case 429 -> throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "TradePilot is at its request limit. Try again later.");
            case 503 -> throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "TradePilot is currently unavailable.");
            case 200 -> {
                try {
                    JsonNode value = mapper.readTree(response.body());
                    if (value instanceof ObjectNode object && object.path("shipment_id").isTextual()
                            && object.path("agents").isObject()) {
                        return object;
                    }
                } catch (JsonProcessingException ignored) {
                    // The caller receives a generic gateway error.
                }
            }
            default -> { }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TradePilot did not return a shipment analysis.");
    }
}
