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

## 2. Authentication & RBAC (Stateless JWT)
All `/api/**` endpoints (except `/api/auth/login`) require `Authorization: Bearer <accessToken>`.
Obtain a token via `POST /api/auth/login` with `{ "username": "...", "password": "..." }`, and inspect the active principal via `GET /api/auth/me`.

| Demo Account | Password | Role | Permitted Actions |
| :--- | :--- | :--- | :--- |
| `analyst@deeptier.local` | `Analyst@123` | `ROLE_ANALYST` | Tier map, CSV import, What-If, AI disruption analysis, logistics reroute, audit verify, create approval drafts, view RFQs |
| `approver@deeptier.local` | `Approver@123` | `ROLE_APPROVER` | All Analyst actions + `POST /api/approvals/{id}/approve`, `/reject`, `POST /api/rfqs/{id}/push-sap` (self-approval blocked) |
| `admin@deeptier.local` | `Admin@123` | `ROLE_ADMIN` | All Approver actions + `POST /api/seed/reset` |

> **Toggle:** Set `DEEPTIER_AUTH_ENABLED=false` (`deeptier.auth.enabled=false`) to disable JWT enforcement during local UI prototyping. `/internal/**` remains protected solely by `X-Internal-Key`.

---

## 3. Frontend API Coverage Matrix

| Frontend Capability | Backend Endpoint(s) | Auth / Role | Required Data | Supported UI States | Verified |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **JWT Login & Session** | `POST /api/auth/login`, `GET /api/auth/me` | Public (`login`), Bearer (`me`) | `username`, `password` | Login form, Bearer token storage, `401` invalid credentials | YES |
| **Demo Seed / Reset** | `POST /api/seed/reset?seed=20261121` | `ROLE_ADMIN` | Optional `seed` | Loading, Reset (`5` nodes, `4` links), `403` non-admin, Error | YES |
| **Multi-Tier Supply Graph** | `GET /api/tier-map/graph`, `/nodes`, `/links` | `ANALYST` / `APPROVER` / `ADMIN` | None | Loading, Populated Graph, Empty (`totalNodes=0`), Error | YES |
| **CSV BOM Onboarding** | `POST /api/supply-chain/import/csv?mode=replace\|append`, `GET /api/supply-chain/import/template` | `ANALYST` / `APPROVER` / `ADMIN` | Multipart `.csv` file (14 columns) | Uploading, Success, Row Validation Error (`400` with line/col), Server Error | YES |
| **What-If Risk Simulation** | `POST /api/whatif/simulate` | `ANALYST` / `APPROVER` / `ADMIN` | Optional `hsCode`, `deltaPercentagePoints` (`BigDecimal`, supports `0` & `12.5`), `annualSpendInr`, `costFraction`, inventory params | Simulating, Results (Exposure + Cover + 10k MC + Ranked Options with `targetSupplierId`, `unitCostInr`, `estimatedLandedCostInr`), Error | YES |
| **AI Disruption Analysis** | `POST /api/agent/analyze-disruption` | `ANALYST` / `APPROVER` / `ADMIN` | `alertText`, optional `partCode`, `annualSpendInr`, `costFraction`, inventory overrides | Analyzing, Brief + Simulation + `backendStatus` (`connected` / `partial` / `fallback`) + `warnings` + `simulationSkipped` (`UNPARSED`), Validation (`400`) | YES |
| **57-Hub Logistics Reroute** | `POST /api/logistics/reroute` | `ANALYST` / `APPROVER` / `ADMIN` | `origin`, `destination`, `cargoDescription`, `weightKg`, `allowedModes`, optional `documents`, `declaredValueUsd`, `incoterm`, `carrierQuotes`, `disruptions`, `impactScenario`, `useDemoDefaults` | Routing, `feasible` vs `decisionStatus` (`ready_for_human_approval` / `blocked`), Waypoint `legs[]` (`legId`, `fromHub`, `toHub`), `costEstimateInr`, `transitHoursEstimate`, Offline `503` | YES |
| **Human Approval Queue** | `POST /api/approvals`, `GET /api/approvals`, `GET /api/approvals/{id}` | `ANALYST` / `APPROVER` / `ADMIN` | `partCode`, `targetSupplierId`, `recommendedQty`, `estimatedLandedCost` + `Idempotency-Key` + optional `scenarioId` | Loading, Empty, Pending List, Created (`201`), Validation (`400`) | YES |
| **Approve / Reject Action** | `POST /api/approvals/{id}/approve`, `/reject` | `ROLE_APPROVER` / `ROLE_ADMIN` | Optional `comment` + `Idempotency-Key` (`reviewerName` auto-populated from JWT; self-approval returns `403`) | Submitting, `AUDITED` + RFQ issued (`200`), `403` self-approval/role, Conflict (`409`), Not Found (`404`) | YES |
| **RFQ List, Payload & Direct S/4HANA Push** | `GET /api/rfqs`, `GET /api/rfqs/{id}/sap-payload`, `POST /api/rfqs/{id}/push-sap` | `GET` (`ANALYST`+), `POST push-sap` (`APPROVER` / `ADMIN`) | RFQ `id` | Loading, List, SAP OData JSON Preview, Direct Push (`mode: simulated_s4hana` or `live_s4hana`, `purchaseRequisitionId`, `status: PUSHED_TO_SAP`), Conflict (`409`), `404` | YES |
| **SHA-256 Audit Log & Verify** | `GET /api/audit`, `/events`, `/scenario/{id}`, `GET /api/audit/verify` | `ANALYST` / `APPROVER` / `ADMIN` | Optional `scenarioId` | Loading, Hash-chained Timeline, Cryptographic Integrity Badge (`valid: true`, `totalEvents`, `brokenAtEventId`) | YES |

### Explicitly Out of Scope
1. **Pagination / WebSockets:** All endpoints are synchronous request/response and return full lists without `page`/`size` parameters.

---

## 4. Core Frontend Workflow Sequences

1. **Login & Dashboard Loading:** Call `POST /api/auth/login` (`analyst@deeptier.local` / `Analyst@123`) and attach `Authorization: Bearer <accessToken>` to all `/api/**` calls. Load `GET /api/tier-map/graph`, `GET /api/approvals`, `GET /api/rfqs`, and `GET /api/audit/verify` in parallel. If `graph.totalNodes === 0`, prompt an Admin (`admin@deeptier.local`) to run `POST /api/seed/reset?seed=20261121` or import a CSV.
2. **CSV Supply-Chain Import:** Upload 14-column CSV to `POST /api/supply-chain/import/csv?mode=replace|append`. On `200`, refresh `GET /api/tier-map/graph`; imported links immediately feed into `GET /internal/tools/get_tier_map` and AI disruption analysis. On `400`, render `ProblemDetail.errors[]`.
3. **AI Disruption Analysis (`POST /api/agent/analyze-disruption`):** Send `alertText` (and optional `annualSpendInr` / `costFraction`). Display `backendStatus` (`connected` vs `fallback`) and `warnings[]`. If `simulationSkipped === true` (`signalType === "UNPARSED"`), show a human-review prompt with `0` exposure and no recovery cards. Otherwise, display unified exposure (identical across `executiveBrief`, `simulation.tariffShock.totalExposureInr`, and audit) and pre-fill **"Create Approval Request"** from Rank-1 `SUP-RJK-CAST` using `estimatedLandedCostInr`.
4. **57-Hub Logistics Reroute (`POST /api/logistics/reroute`):** Send shipment + `documents` / `declaredValueUsd` / `incoterm` (or `useDemoDefaults: true` for quick demo). Render `feasible` (physical route capacity) separately from `decisionStatus` (`ready_for_human_approval` vs `blocked`), `legs[]` (`legId`, `fromHub` -> `toHub`), `costEstimateInr`, `transitHoursEstimate`, and `decision.gateReasons[]`. Handle `503` gracefully if Python `:8000` is offline.
5. **HITL Approval -> Direct S/4HANA Push -> Audit Verify:** As `analyst@deeptier.local`, generate a UUID `Idempotency-Key` header for `POST /api/approvals` (using `targetSupplierId: "SUP-RJK-CAST"` and `estimatedLandedCost` from `RouteOption.estimatedLandedCostInr`). Switch to `approver@deeptier.local` for `POST /api/approvals/{id}/approve` (self-approval is blocked with `403`). On `200`, preview `GET /api/rfqs/{rfqId}/sap-payload`, execute `POST /api/rfqs/{rfqId}/push-sap` (`mode: "simulated_s4hana"` or `"live_s4hana"` when `S4HANA_BASE_URL` is configured), and verify the SHA-256 chain via `GET /api/audit/verify`.
