# DeepTier Frontend Integration Guide (React Cockpit)
**Backend Base URL:** `http://127.0.0.1:8080` (Java Spring Boot — single frontend entrypoint)  
**AI Service:** `http://127.0.0.1:8000` (Called server-side by Java; frontend should **never** hold `DEEP_TIER_INTERNAL_KEY` or `TRADEPILOT_API_KEY`)  
**Machine-Readable Specs:** [java-openapi.json](file:///C:/Users/Gaurav%20Urmaliya/Documents/DEEPTIER/openapi/java-openapi.json) | [python-openapi.json](file:///C:/Users/Gaurav%20Urmaliya/Documents/DEEPTIER/openapi/python-openapi.json)

---

## 1. Frontend API Coverage Matrix

| Frontend Capability | Backend Endpoint(s) | Auth | Required Data | Supported UI States | Verified |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **Demo Seed / Reset** | `POST /api/seed/reset?seed=20261121` | Public | Optional `seed` | Loading, Success, Error | YES |
| **Multi-Tier Supply Graph** | `GET /api/tier-map/graph`, `/nodes`, `/links` | Public | None | Loading, Populated Graph, Empty (`totalNodes=0`), Error | YES |
| **CSV BOM Onboarding** | `POST /api/supply-chain/import/csv?mode=replace\|append` | Public | Multipart `.csv` file (14 columns) | Uploading, Success, Row Validation Error (`400` with line/col), Server Error | YES |
| **What-If Risk Simulation** | `POST /api/whatif/simulate` | Public | Optional `hsCode`, `deltaPercentagePoints`, inventory params | Simulating, Results (Exposure + Cover + 10k MC + Ranked Options), Error | YES |
| **AI Disruption Analysis** | `POST /api/agent/analyze-disruption` | Public | `alertText`, optional `partCode` | Analyzing, Brief + Simulation + `extractionEngine` badge, Validation (`400`), Offline Fallback | YES |
| **Human Approval Queue** | `POST /api/approvals`, `GET /api/approvals`, `GET /api/approvals/{id}` | Public | `partCode`, `targetSupplierId`, `recommendedQty` + `Idempotency-Key` | Loading, Empty, Pending List, Created (`201`), Validation (`400`) | YES |
| **Approve / Reject Action** | `POST /api/approvals/{id}/approve`, `/reject` | Public | `reviewerName`, optional `comment` + `Idempotency-Key` | Submitting, `AUDITED` + RFQ issued (`200`), Conflict (`409`), Not Found (`404`) | YES |
| **RFQ List & S/4HANA Handoff** | `GET /api/rfqs`, `GET /api/rfqs/{id}/sap-payload` | Public | RFQ `id` | Loading, List, SAP OData JSON Preview, Conflict (`409` if not audited), `404` | YES |
| **SHA-256 Audit Log** | `GET /api/audit`, `/api/audit/events`, `/scenario/{id}` | Public | Optional `scenarioId` | Loading, Hash-chained Timeline, Empty, Error | YES |

### Explicitly Missing Backend Capabilities (Do Not Mock Silently)
1. **End-User Authentication / Login / RBAC:** `/api/*` endpoints are currently unauthenticated (`Public`). If the UI needs user login/roles, it must manage mock personas client-side (e.g. passing `reviewerName` on `/approve`) until Spring Security/OAuth2 is added.
2. **Direct S/4HANA Push Execution:** `GET /api/rfqs/{id}/sap-payload` generates the clean OData `API_PURCHASEREQ_PROCESS_SRV` payload, but there is **no** `POST /api/rfqs/{id}/push-to-sap` endpoint yet (by Clean Core design, S/4HANA posting is previewed/exported only).
3. **Backup Supplier CRUD Endpoint:** `BackupSupplier` entities are seeded and ranked inside `OptimizerService` (`/api/whatif/simulate`), but there is no standalone `GET /api/backup-suppliers` endpoint.
4. **Pagination / Filtering:** `GET /api/approvals`, `GET /api/rfqs`, and `GET /api/audit` return full lists (no `page`/`size` query params yet).
5. **WebSockets / SSE:** All Java endpoints are synchronous request/response; there is no WebSocket push channel.

---

## 2. Complete Workflow Sequences

### Workflow 1: Dashboard Data Loading
1. **Sequence:** On mount, call `GET /api/tier-map/graph`, `GET /api/approvals`, `GET /api/rfqs`, and `GET /api/audit` in parallel (`Promise.all`).
2. **Empty State:** If `graph.totalNodes === 0`, prompt the user to click **"Load Hackfest Demo (`HPH-1042`)"** (`POST /api/seed/reset?seed=20261121`) or **"Import CSV"**.
3. **Transformations:** Map `nodes` (`tier` 0..3, `linkType` `DECLARED`/`INFERRED`) and `links` (`fromSupplierId` -> `toSupplierId`, `annualSpendInr`) into React Flow / graph edges.
4. **Sync/Async:** Synchronous; timeout `15s` (`45s` for remote HANA reset).

### Workflow 2: Supply-Chain CSV Import & Refresh
1. **Sequence:** User selects `.csv` file and mode (`replace` or `append`) -> `POST /api/supply-chain/import/csv?mode=...` with `FormData` (`file`).
2. **Success (`200`):** Show toast (`nodesCreated`, `linksCreated`), then immediately re-fetch `GET /api/tier-map/graph` and `GET /api/audit`.
3. **Validation Error (`400`):** Parse `ProblemDetail.errors[]` (`line`, `column`, `value`, `message`) and render an inline row-error table. Backend rolls back atomically so partial rows are never saved.

### Workflow 3: Risk & What-If Simulation
1. **Sequence:** User adjusts tariff delta (`deltaPercentagePoints`), `hsCode`, `unitsOnHand`, `dailyDemand`, `leadTimeDays` -> `POST /api/whatif/simulate`.
2. **Response Rendering:**
   - **Tariff Exposure Card:** `tariffShock.totalExposureInr`, `lineItems[]`.
   - **Inventory Cover Gauge:** `inventory.coverDaysDisplay` (`"UNKNOWN"` if `dailyDemand === 0`), `stockoutDate`, `gapDays`.
   - **Monte Carlo Chart:** `monteCarlo.arrivalDaysP10`, `P50`, `P90`, `stockoutProbability`.
   - **Recovery Ranking Table:** `recoveryOptions[]` sorted by `rank` (highlight `recommended: true`), plus `rejectedOptions[]` with `reasonCode`.

### Workflow 4: Disruption Analysis & AI-Generated Insights
1. **Sequence:** User pastes news/customs alert (`alertText`) -> `POST /api/agent/analyze-disruption`.
2. **Timeout & Resilience:** Java calls Python (`:8000/api/v1/deeptier/analyze`) with an 8s timeout; frontend should set a `15s` request timeout. If Python is offline, Java returns `200 OK` with `extractionEngine: "JAVA_DETERMINISTIC_FALLBACK"` — display a subtle badge showing which engine extracted the parameters.
3. **Follow-up:** Render `executiveBrief`, `evidenceIds` chips, and `simulation` results; offer a **"Create Approval Request"** button pre-filled from `simulation.recoveryOptions[0]`.

### Workflow 5: Approval Creation & Execution (HITL Governance)
1. **Idempotency:** Frontend **must** generate a UUID `Idempotency-Key` header per user submit action (and reuse it on network retry to prevent duplicate approvals/RFQs).
2. **Create:** `POST /api/approvals` -> `201 Created` (`approvalStatus: "PENDING_APPROVAL"`).
3. **Decide:** Reviewer enters `reviewerName` and `comment` -> `POST /api/approvals/{id}/approve` (or `/reject`).
4. **Error Handling:** `409 Conflict` means another reviewer already finalized this approval — refresh `GET /api/approvals/{id}` and display the current state.

### Workflow 6: RFQ Generation & SAP Payload Handoff
1. **Sequence:** Upon `200 OK` from `/approve`, response contains `rfq` (`DISPATCHED_MOCK`, ID `RFQ-2026-...`).
2. **SAP Preview:** Click **"View S/4HANA OData Payload"** -> `GET /api/rfqs/{rfqId}/sap-payload`.
3. **States:** `200` displays `sapService` (`API_PURCHASEREQ_PROCESS_SRV`) and `payload.to_PurchaseReqnItem.results[]`; `409` indicates the approval is not yet `AUDITED`.

### Workflow 7: Audit & Governance Trail
1. **Sequence:** `GET /api/audit` (or `/api/audit/events/scenario/{scenarioId}`).
2. **Verification UI:** Display `occurredAt`, `eventType`, `actor`, `entityType`, `beforeHash`, and `afterHash` (truncating hashes to 12 chars with copy-to-clipboard) to prove tamper-evident SHA-256 chaining.
