package com.deeptier.api;

import com.deeptier.api.dto.SeedResetResponse;
import com.deeptier.seed.SeedGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/seed")
@RequiredArgsConstructor
@Tag(name = "Seed", description = "Demo data management - load/reset the deterministic HPH-1042 scenario")
public class SeedController {

    private final SeedGenerator seedGenerator;

    @PostMapping("/reset")
    @Operation(summary = "Reset to seed scenario",
               description = "Wipes all data and loads the deterministic Hydraulic Pump Housing demo. " +
                             "Use seed=20261121 for the official Hackfest demo.")
    public ResponseEntity<SeedResetResponse> reset(
            @RequestParam(defaultValue = "20261121") long seed) {

        seedGenerator.reset(seed);

        return ResponseEntity.ok(new SeedResetResponse(
                "RESET_COMPLETE",
                seed,
                "SYNTHETIC",
                "HPH-1042 Hydraulic Pump Housing",
                3,
                2,
                Instant.now()
        ));
    }
}
