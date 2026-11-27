package com.deeptier.sap;

import com.deeptier.api.dto.SapPurchaseRequisitionPayload;
import com.deeptier.api.dto.SapPushResponse;
import com.deeptier.approval.MockRfq;
import com.deeptier.approval.MockRfqRepository;
import com.deeptier.approval.ResourceNotFoundException;
import com.deeptier.audit.AuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Executes Direct S/4HANA Purchase Requisition push for an AUDITED MockRfq.
 * Supports live S/4HANA OData V2 (CSRF fetch + POST) when S4HANA_BASE_URL is configured,
 * and deterministic simulated S/4HANA mode otherwise.
 */
@Slf4j
@Service
public class SapPushService {

    private static final String PR_ODATA_PATH =
            "/sap/opu/odata/sap/API_PURCHASEREQ_PROCESS_SRV/A_PurchaseRequisitionHeader";

    private final MockRfqRepository mockRfqRepo;
    private final SapPayloadService sapPayloadService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final String s4BaseUrl;
    private final String s4Username;
    private final String s4Password;
    private final String s4BearerToken;

    public SapPushService(
            MockRfqRepository mockRfqRepo,
            SapPayloadService sapPayloadService,
            AuditService auditService,
            ObjectMapper objectMapper,
            @Value("${S4HANA_BASE_URL:}") String s4BaseUrl,
            @Value("${S4HANA_USERNAME:}") String s4Username,
            @Value("${S4HANA_PASSWORD:}") String s4Password,
            @Value("${S4HANA_BEARER_TOKEN:}") String s4BearerToken
    ) {
        this.mockRfqRepo = mockRfqRepo;
        this.sapPayloadService = sapPayloadService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.s4BaseUrl = s4BaseUrl == null ? "" : s4BaseUrl.trim();
        this.s4Username = s4Username == null ? "" : s4Username.trim();
        this.s4Password = s4Password == null ? "" : s4Password;
        this.s4BearerToken = s4BearerToken == null ? "" : s4BearerToken.trim();
    }

    @Transactional
    public SapPushResponse pushToSap(String rfqId, String idempotencyKey) {
        MockRfq rfq = mockRfqRepo.findById(rfqId)
                .orElseThrow(() -> new ResourceNotFoundException("MockRfq not found with id: " + rfqId));

        if ("PUSHED_TO_SAP".equals(rfq.getRfqStatus()) && rfq.getSapPurchaseRequisitionId() != null) {
            return new SapPushResponse(
                    rfq.getSapPushMode() != null ? rfq.getSapPushMode() : "simulated_s4hana",
                    rfq.getId(),
                    rfq.getSapPurchaseRequisitionId(),
                    rfq.getRfqStatus(),
                    rfq.getSapPushedAt() != null ? rfq.getSapPushedAt() : Instant.now()
            );
        }

        // Validates RFQ, AUDITED approval status, and constructs OData V2 payload
        SapPurchaseRequisitionPayload payload = sapPayloadService.buildPurchaseRequisitionPayload(rfqId, idempotencyKey);
        String actor = resolveActor();
        Instant pushedAt = Instant.now();

        String mode;
        String purchaseRequisitionId;
        if (!s4BaseUrl.isBlank()) {
            mode = "live_s4hana";
            purchaseRequisitionId = pushLiveOData(payload);
        } else {
            mode = "simulated_s4hana";
            purchaseRequisitionId = String.format("PR-S4-%08d",
                    ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000));
        }

        rfq.setRfqStatus("PUSHED_TO_SAP");
        rfq.setSapPurchaseRequisitionId(purchaseRequisitionId);
        rfq.setSapPushMode(mode);
        rfq.setSapPushedAt(pushedAt);
        mockRfqRepo.save(rfq);

        auditService.logEvent(
                null,
                "SAP_PR_PUSHED",
                actor,
                "MockRfq",
                rfq.getId(),
                String.format("Pushed RFQ %s to S/4HANA (%s) -> PurchaseRequisition %s",
                        rfq.getId(), mode, purchaseRequisitionId)
        );

        log.info("RFQ pushed to S/4HANA: rfqId={}, mode={}, prId={}, actor={}",
                rfq.getId(), mode, purchaseRequisitionId, actor);

        return new SapPushResponse(mode, rfq.getId(), purchaseRequisitionId, "PUSHED_TO_SAP", pushedAt);
    }

    private String pushLiveOData(SapPurchaseRequisitionPayload payload) {
        String endpoint = s4BaseUrl.replaceAll("/+$", "") + PR_ODATA_PATH;
        String authHeader = buildAuthHeader();

        try {
            HttpRequest.Builder csrfReqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("X-CSRF-Token", "Fetch")
                    .header("Accept", "application/json")
                    .GET();
            if (authHeader != null) {
                csrfReqBuilder.header("Authorization", authHeader);
            }

            HttpResponse<String> csrfRes = httpClient.send(
                    csrfReqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (csrfRes.statusCode() < 200 || csrfRes.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "S/4HANA CSRF fetch failed with HTTP " + csrfRes.statusCode());
            }

            String csrfToken = csrfRes.headers().firstValue("X-CSRF-Token")
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "S/4HANA did not return X-CSRF-Token"));

            List<String> cookies = csrfRes.headers().allValues("Set-Cookie");
            String cookieHeader = cookies.stream()
                    .map(c -> c.split(";", 2)[0])
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");

            String bodyJson = objectMapper.writeValueAsString(payload.payload());
            HttpRequest.Builder postBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-CSRF-Token", csrfToken)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson));
            if (authHeader != null) {
                postBuilder.header("Authorization", authHeader);
            }
            if (!cookieHeader.isBlank()) {
                postBuilder.header("Cookie", cookieHeader);
            }

            HttpResponse<String> postRes = httpClient.send(
                    postBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (postRes.statusCode() < 200 || postRes.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "S/4HANA PR creation failed with HTTP " + postRes.statusCode());
            }

            JsonNode root = objectMapper.readTree(postRes.body());
            JsonNode d = root.has("d") ? root.path("d") : root;
            String prId = d.path("PurchaseRequisition").asText("").trim();
            return prId.isEmpty() ? payload.correlation().deepTierRfqId() : prId;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to push PurchaseRequisition to S/4HANA: " + ex.getMessage(), ex);
        }
    }

    private String buildAuthHeader() {
        if (!s4BearerToken.isBlank()) {
            return "Bearer " + s4BearerToken;
        }
        if (!s4Username.isBlank()) {
            String raw = s4Username + ":" + s4Password;
            return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    private String resolveActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "system";
    }
}
