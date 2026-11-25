package com.deeptier.api;

import com.deeptier.sap.TradePilotClient;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/tools/tradepilot")
@SecurityRequirement(name = "InternalKey")
public class TradePilotController {
    private final TradePilotClient client;

    public TradePilotController(TradePilotClient client) {
        this.client = client;
    }

    @PostMapping(path = "/shipments/analyze", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ObjectNode> analyze(@RequestBody ObjectNode shipment) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(client.analyze(shipment));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleTradePilotFailure(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleInvalidJson() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Expected a shipment JSON object."));
    }
}
