package com.deeptier.api;

import com.deeptier.api.dto.RerouteRequest;
import com.deeptier.api.dto.RerouteResponse;
import com.deeptier.sap.AgentGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/logistics")
@RequiredArgsConstructor
@Tag(name = "Logistics Routing", description = "57-hub India road & multimodal rerouting proxy via Python TradePilot")
public class LogisticsRoutingController {

    private final AgentGateway agentGateway;

    @PostMapping("/reroute")
    @Operation(summary = "Compute 57-hub multimodal reroute",
            description = "Validates shipment request via typed Java DTOs and proxies to TradePilot's "
                    + "57-hub India road/multimodal graph on Python :8000 with an 8s bounded timeout.")
    public ResponseEntity<RerouteResponse> reroute(@Valid @RequestBody RerouteRequest request) {
        log.info("Proxying 57-hub reroute request: {} -> {} ({} kg)",
                request.origin(), request.destination(), request.weightKg());
        return ResponseEntity.ok(agentGateway.rerouteShipment(request));
    }
}
