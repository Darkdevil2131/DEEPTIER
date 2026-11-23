package com.deeptier.api;

import com.deeptier.api.dto.TierMapGraphResponse;
import com.deeptier.domain.SupplierLink;
import com.deeptier.domain.SupplierNode;
import com.deeptier.engine.TierMapService;
import com.deeptier.repo.SupplierLinkRepository;
import com.deeptier.repo.SupplierNodeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tier-map")
@RequiredArgsConstructor
@Tag(name = "TierMap", description = "Supplier network graph - nodes and links with evidence quality")
public class TierMapController {

    private final SupplierNodeRepository nodeRepo;
    private final SupplierLinkRepository linkRepo;
    private final TierMapService tierMapService;

    @GetMapping("/nodes")
    @Operation(summary = "List all supplier nodes")
    public ResponseEntity<List<SupplierNode>> listNodes() {
        return ResponseEntity.ok(nodeRepo.findAll());
    }

    @GetMapping("/links")
    @Operation(summary = "List all supplier links")
    public ResponseEntity<List<SupplierLink>> listLinks() {
        return ResponseEntity.ok(linkRepo.findAll());
    }

    @GetMapping("/graph")
    @Operation(summary = "Get full tier map as graph (nodes + links)",
               description = "Returns the complete supplier graph ready for D3/SAPUI5 rendering.")
    public ResponseEntity<TierMapGraphResponse> graph() {
        var g = tierMapService.getGraph();
        return ResponseEntity.ok(new TierMapGraphResponse(
                g.nodes(),
                g.links(),
                g.dataClass().name(),
                g.totalNodes(),
                g.hiddenTiersCount()
        ));
    }
}
