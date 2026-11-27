package com.deeptier.api;

import com.deeptier.api.dto.SeedResetResponse;
import com.deeptier.repo.SupplierLinkRepository;
import com.deeptier.repo.SupplierNodeRepository;
import com.deeptier.seed.SeedGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestController
@RequestMapping("/api/seed")
@RequiredArgsConstructor
@Tag(name = "Seed", description = "Demo data management - load/reset the deterministic HPH-1042 scenario")
public class SeedController {

    private final SeedGenerator seedGenerator;
    private final SupplierNodeRepository nodeRepo;
    private final SupplierLinkRepository linkRepo;

    @Value("${deeptier.demo.reset-enabled:true}")
    private boolean resetEnabled = true;

    @PostMapping("/reset")
    @Operation(summary = "Reset to seed scenario",
               description = "Wipes all data and loads the deterministic Hydraulic Pump Housing demo. " +
                             "Use seed=20261121 for the official Hackfest demo. Gated by deeptier.demo.reset-enabled.")
    public ResponseEntity<SeedResetResponse> reset(
            @RequestParam(defaultValue = "20261121") long seed) {

        if (!resetEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Demo reset is disabled in this environment (deeptier.demo.reset-enabled=false).");
        }

        seedGenerator.reset(seed);

        return ResponseEntity.ok(new SeedResetResponse(
                "RESET_COMPLETE",
                seed,
                "SYNTHETIC",
                "HPH-1042 Hydraulic Pump Housing",
                (int) nodeRepo.count(),
                (int) linkRepo.count(),
                Instant.now()
        ));
    }
}
