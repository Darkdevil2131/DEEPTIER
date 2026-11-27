# DeepTier Frontend Integration Guide (React / SAP Fiori Cockpit)
**Single Frontend Entrypoint:** `http://127.0.0.1:8080` (Java Spring Boot `dt-backend`)  
**Internal AI Service:** `http://127.0.0.1:8000` (Python FastAPI/LangGraph — called server-side by Java only via `X-Internal-Key` / `DEEPTIER_INTERNAL_KEY`. The frontend **never** calls Python directly and **never** holds `DEEPTIER_INTERNAL_KEY` or `TRADEPILOT_API_KEY`.)  
**Machine-Readable OpenAPI Specs:** [java-openapi.json](./openapi/java-openapi.json) | [python-openapi.json](./openapi/python-openapi.json)

---

## 1. Headline Demo Narrative (`HPH-1042` / Seed `20261121`)
- **Scenario:** Pune Hydraulic OEM (`SUP-PUNE-OEM`) sources machined castings from Tier-1 Alpha Machining (`SUP-CBE-ALPHA`, Coimbatore) and hidden Tier-2 Beta Foundry (`SUP-NGB-BETA`, Ningbo, China, `HS 7325`, `60%` of ₹48,00,000 spend).
- **Shock:** `+20%` customs tariff on `HS 7325` from China + 15-day inventory cover gap (`300` units, `20/day` demand, `30d` lead time, ₹80,000/day stoppage).
- **Deterministic Outcome:**
  - **Duty Exposure:** `₹5,76,000` (`48,00,000 * 0.60 * 0.20`).
  - **Baseline (`WAIT`):** `₹17,76,000` total impact (`₹5,76,000` duty + `₹12,00,000` stoppage loss).
  - **Rank 1 (`recommended: true`): Rajkot Foundry Cluster (`SUP-RJK-CAST`)** — domestic backup reroute at **~₹5.18L** total impact (`₹1.80L` freight + `₹10K` handling + `₹8K` insurance + `₹0` duty + `₹3.20L` residual 4-day gap), **saving ~₹12.58L** over waiting and flowing directly into `POST /api/approvals` -> `GET /api/rfqs/{id}/sap-payload`.

---

## 2. Frontend API Coverage Matrix

| Frontend Capability | Backend Endpoint(s) | Auth | Required Data | Supported UI States | Verified |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **Demo Seed / Reset** | `POST /api/seed/reset?seed=20261121` | Public | Optional `seed` | Loading, Reset (`5` nodes, `4` links), Error | YES |
| **Multi-Tier Supply Graph** | `GET /api/tier-map/graph`, `/nodes`, `/links` | Public | None | Loading, Populated Graph, Empty (`totalNodes=0`), Error | YES |
| **CSV BOM Onboarding** | `POST /api/supply-chain/import/csv?mode=replace\|append` | Public | Multipart `.csv` file (14 columns) | Uploading, Success, Row Validation Error (`400` with line/col), Server Error | YES |
| **What-If Risk Simulation** | `POST /api/whatif/simulate` | Public | Optional `hsCode`, `deltaPercentagePoints` (`BigDecimal`, supports `0` & `12.5`), `annualSpendInr`, `costFraction`, inventory params | Simulating, Results (Exposure + Cover + 10k MC + Ranked Options with `targetSupplierId`, `unitCostInr`, `estimatedLandedCostInr`), Error | YES |
| **AI Disruption Analysis** | `POST /api/agent/analyze-disruption` | Public | `alertText`, optional `partCode`, `annualSpendInr`, `costFraction`, inventory overrides | Analyzing, Brief + Simulation + `backendStatus` (`connected` / `partial` / `fallback`) + `warnings` + `simulationSkipped` (`UNPARSED`), Validation (`400`) | YES |
| **57-Hub Logistics Reroute** | `POST /api/logistics/reroute` | Public | `origin`, `destination`, `cargoDescription`, `weightKg`, `allowedModes`, optional `documents`, `declaredValueUsd`, `incoterm`, `carrierQuotes`, `disruptions`, `impactScenario`, `useDemoDefaults` | Routing, `feasible` vs `decisionStatus` (`ready_for_human_approval` / `blocked`), Waypoint `legs[]` (`legId`, `fromHub`, `toHub`), `costEstimateInr`, `transitHoursEstimate`, Offline `503` | YES |
| **Human Approval Queue** | `POST /api/approvals`, `GET /api/approvals`, `GET /api/approvals/{id}` | Public | `partCode`, `targetSupplierId`, `recommendedQty`, `estimatedLandedCost` + `Idempotency-Key` + optional `scenarioId` | Loading, Empty, Pending List, Created (`201`), Validation (`400`) | YES |
| **Approve / Reject Action** | `POST /api/approvals/{id}/approve`, `/reject` | Public | `reviewerName`, optional `comment` + `Idempotency-Key` | Submitting, `AUDITED` + RFQ issued (`200`), Conflict (`409`), Not Found (`404`) | YES |
| **RFQ List & S/4HANA Handoff** | `GET /api/rfqs`, `GET /api/rfqs/{id}/sap-payload` | Public | RFQ `id` | Loading, List, SAP OData JSON Preview (`API_PURCHASEREQ_PROCESS_SRV` using `unitCostInr` / `estimatedLandedCostInr`), Conflict (`409`), `404` | YES |
| **SHA-256 Audit Log & Verify** | `GET /api/audit`, `/events`, `/scenario/{id}`, `GET /api/audit/verify` | Public | Optional `scenarioId` | Loading, Hash-chained Timeline, Cryptographic Integrity Badge (`valid: true`, `totalEvents`, `brokenAtEventId`) | YES |

### Explicitly Missing Backend Capabilities
1. **End-User Authentication / RBAC:** `/api/*` endpoints are currently unauthenticated (`Public`). Pass `reviewerName` on `/approve` and `/reject`.
2. **Direct S/4HANA Push Execution:** `GET /api/rfqs/{id}/sap-payload` generates the clean OData `API_PURCHASEREQ_PROCESS_SRV` payload for preview/export; there is no live S/4HANA HTTP POST endpoint.
3. **Pagination / WebSockets:** All endpoints are synchronous request/response and return full lists without `page`/`size` parameters.

---

## 3. Core Frontend Workflow Sequences

1. **Dashboard Loading:** Call `GET /api/tier-map/graph`, `GET /api/approvals`, `GET /api/rfqs`, and `GET /api/audit/verify` in parallel. If `graph.totalNodes === 0`, prompt to run `POST /api/seed/reset?seed=20261121` or CSV import.
2. **CSV Supply-Chain Import:** Upload 14-column CSV to `POST /api/supply-chain/import/csv?mode=replace|append`. On `200`, refresh `GET /api/tier-map/graph`; imported links immediately feed into `GET /internal/tools/get_tier_map` and AI disruption analysis. On `400`, render `ProblemDetail.errors[]`.
3. **AI Disruption Analysis (`POST /api/agent/analyze-disruption`):** Send `alertText` (and optional `annualSpendInr` / `costFraction`). Display `backendStatus` (`connected` vs `fallback`) and `warnings[]`. If `simulationSkipped === true` (`signalType === "UNPARSED"`), show a human-review prompt with `0` exposure and no recovery cards. Otherwise, display unified exposure (identical across `executiveBrief`, `simulation.tariffShock.totalExposureInr`, and audit) and pre-fill **"Create Approval Request"** from Rank-1 `SUP-RJK-CAST` using `estimatedLandedCostInr`.
4. **57-Hub Logistics Reroute (`POST /api/logistics/reroute`):** Send shipment + `documents` / `declaredValueUsd` / `incoterm` (or `useDemoDefaults: true` for quick demo). Render `feasible` (physical route capacity) separately from `decisionStatus` (`ready_for_human_approval` vs `blocked`), `legs[]` (`legId`, `fromHub` -> `toHub`), `costEstimateInr`, `transitHoursEstimate`, and `decision.gateReasons[]`. Handle `503` gracefully if Python `:8000` is offline.
5. **HITL Approval -> SAP PR Handoff -> Audit Verify:** Generate a UUID `Idempotency-Key` header for `POST /api/approvals` (using `targetSupplierId: "SUP-RJK-CAST"` and `estimatedLandedCost` from `RouteOption.estimatedLandedCostInr`) and `POST /api/approvals/{id}/approve`. On `200`, fetch `GET /api/rfqs/{rfqId}/sap-payload` and verify the chain via `GET /api/audit/verify`.
