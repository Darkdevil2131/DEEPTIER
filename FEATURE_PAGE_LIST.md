# DeepTier Frontend Feature & Page List

**Backend Entrypoint:** `http://127.0.0.1:8080` (`dt-backend` — Java 21 / Spring Boot 3.3.5)  
**OpenAPI Specs:** [java-openapi.json](./openapi/java-openapi.json) | [Integration Guide](./FRONTEND_INTEGRATION_GUIDE.md)

---

## Pages & Feature Breakdown

### 1. Login & Sign-Up Page (`/login`, `/register`)
- **Access:** Public (unauthenticated)
- **Endpoints:**
  - `POST /api/auth/login` — `{ username, password }` → `{ accessToken, tokenType, username, role }` (`401` on invalid credentials)
  - `POST /api/auth/register` — `{ username, password }` → `201 Created` `{ accessToken, tokenType, username, role: "ROLE_ANALYST" }` (`409` on duplicate user)
  - `GET /api/auth/me` — Bearer token → `{ username, role }`
- **UI Features:**
  - Login form + Sign-Up tab (`ROLE_ANALYST` registration persisted in `app_user`).
  - One-click Demo Persona Switcher (`analyst@deeptier.local` / `Analyst@123`, `approver@deeptier.local` / `Approver@123`, `admin@deeptier.local` / `Admin@123`).
  - Role badge in top navbar (`ROLE_ANALYST`, `ROLE_APPROVER`, `ROLE_ADMIN`) controlling action visibility.

---

### 2. Executive Risk Cockpit / Overview (`/`)
- **Access:** `ROLE_ANALYST`, `ROLE_APPROVER`, `ROLE_ADMIN`
- **Endpoints:**
  - `GET /api/tier-map/graph` — summary counts (`totalNodes`, `totalLinks`) + graph data
  - `GET /api/approvals` — pending & audited approval queue counts
  - `GET /api/rfqs` — issued & SAP-pushed RFQ counts
  - `GET /api/audit/verify` — SHA-256 ledger integrity badge (`valid: true`, `totalEvents`)
  - `POST /api/seed/reset?seed=20261121` — **Admin-only** (`ROLE_ADMIN`) demo baseline reset (`5` nodes, `4` links, `4` backup suppliers)
- **UI Features:**
  - KPI strip: Active Exposure, Pending Approvals, RFQs / SAP PRs, SHA-256 Audit Integrity Badge.
  - Empty-state onboarding prompt (Seed Demo or Upload CSV) when `totalNodes === 0`.

---

### 3. Multi-Tier Supply Chain Graph & BOM Onboarding (`/supply-chain`)
- **Access:** `ROLE_ANALYST`, `ROLE_APPROVER`, `ROLE_ADMIN`
- **Endpoints:**
  - `GET /api/tier-map/graph`, `GET /api/tier-map/nodes`, `GET /api/tier-map/links`
  - `GET /api/supply-chain/import/template` — downloads 14-column sample CSV (`text/csv`)
  - `POST /api/supply-chain/import/csv?mode=replace|append` — multipart `.csv` upload
- **UI Features:**
  - Interactive Tier-0 → Tier-1 → Tier-2+ network graph (nodes, countries, HS codes, annual spend, lead times, confidence).
  - CSV Drag-and-Drop Importer with **Download Template** button and line/column validation error table (`ProblemDetail.errors[]` on `400`).

---

### 4. AI Disruption Analyzer (`/disruptions`)
- **Access:** `ROLE_ANALYST`, `ROLE_APPROVER`, `ROLE_ADMIN`
- **Endpoints:**
  - `POST /api/agent/analyze-disruption` — `{ alertText, partCode?, annualSpendInr?, costFraction?, scenarioId?, ... }`
- **UI Features:**
  - Natural-language alert input (e.g., `"20% tariff on HSN 7325 castings from China"`).
  - Extraction metadata pills: `extractionEngine` (e.g., `gemini:gemini-3.5-flash-lite` or `deterministic_regex_fallback`), `backendStatus` (`connected` / `partial` / `fallback`), `warnings[]`.
  - Grounded `executiveBrief` + unified exposure & inventory cover cards + ranked recovery options.
  - `UNPARSED` handling (`simulationSkipped: true`) showing human-review notice without fabricated numbers.
  - One-click **"Send Rank-1 to Approval Queue"** pre-filling `POST /api/approvals`.

---

### 5. What-If Tariff & Monte Carlo Simulator (`/what-if`)
- **Access:** `ROLE_ANALYST`, `ROLE_APPROVER`, `ROLE_ADMIN`
- **Endpoints:**
  - `POST /api/whatif/simulate` — `{ hsCode, originCountry?, deltaPercentagePoints, annualSpendInr?, costFraction?, unitsOnHand?, dailyDemand?, leadTimeDays?, scenarioId? }`
- **UI Features:**
  - Interactive sliders/inputs for tariff shock (`0%`, decimal `12.5%`, `20%`), annual spend, cost fraction, and inventory parameters.
  - Duty exposure breakdown, days-of-cover / gap-days gauge, 10,000-trial Monte Carlo distribution summary, and ranked supplier recovery comparison (`unitCostInr`, `estimatedLandedCostInr`, `recommended: true`).

---

### 6. 57-Hub Logistics Reroute Planner (`/logistics`)
- **Access:** `ROLE_ANALYST`, `ROLE_APPROVER`, `ROLE_ADMIN`
- **Endpoints:**
  - `POST /api/logistics/reroute` — `{ origin, destination, cargoDescription, weightKg, allowedModes, documents?, declaredValueUsd?, incoterm?, carrierQuotes?, disruptions?, impactScenario?, useDemoDefaults? }`
- **UI Features:**
  - Shipment & mode selector (`air`, `sea`, `road`, `rail`) + compliance document attachments.
  - Clear split between **Physical Route Feasibility (`feasible`)** and **Commercial Readiness (`decisionStatus`: `ready_for_human_approval` vs `blocked`)**.
  - Multi-leg route timeline (`legs[]`: `legId`, `fromHub` → `toHub`), `costEstimateInr`, `transitHoursEstimate`, and `decision.gateReasons[]`.
  - Graceful `503` banner if Python AI service (`:8000`) is offline.

---

### 7. Human-in-the-Loop (HITL) Approval Queue (`/approvals`)
- **Access:**
  - View & Create Drafts: `ROLE_ANALYST`, `ROLE_APPROVER`, `ROLE_ADMIN`
  - Approve / Reject: **`ROLE_APPROVER`, `ROLE_ADMIN` only** (`ROLE_ANALYST` receives `403`; self-approval blocked with `403`)
- **Endpoints:**
  - `GET /api/approvals?status=PENDING_APPROVAL|AUDITED|...`
  - `GET /api/approvals/{id}`
  - `POST /api/approvals` (with `Idempotency-Key` header)
  - `POST /api/approvals/{id}/approve` (with `Idempotency-Key` header)
  - `POST /api/approvals/{id}/reject` (with `Idempotency-Key` header)
- **UI Features:**
  - Approval cards showing `requestedBy`, `reviewerName`, status lifecycle (`PENDING_APPROVAL` → `APPROVED`/`REJECTED` → `RFQ_ISSUED` → `AUDITED`), and conflict (`409`) / permission (`403`) feedback.

---

### 8. RFQ & Direct SAP S/4HANA Execution (`/rfqs`)
- **Access:**
  - View RFQs & Preview SAP Payload: `ROLE_ANALYST`, `ROLE_APPROVER`, `ROLE_ADMIN`
  - Execute Direct S/4HANA Push: **`ROLE_APPROVER`, `ROLE_ADMIN` only** (`ROLE_ANALYST` receives `403`)
- **Endpoints:**
  - `GET /api/rfqs`
  - `GET /api/rfqs/{id}/sap-payload` — OData V2 `API_PURCHASEREQ_PROCESS_SRV` JSON payload with `auditHash`
  - `POST /api/rfqs/{id}/push-sap` — Live OData CSRF+POST (when `S4HANA_BASE_URL` is set) or Simulated ERP push (`mode: "simulated_s4hana"`, `purchaseRequisitionId: "PR-S4-..."`, `status: "PUSHED_TO_SAP"`)
- **UI Features:**
  - RFQ table with `rfqStatus` (`DISPATCHED_MOCK` vs `PUSHED_TO_SAP`).
  - SAP OData V2 JSON Payload Inspector drawer.
  - **"Push to SAP S/4HANA"** action button displaying the returned Purchase Requisition ID (`PR-S4-...`) and timestamp.

---

### 9. SHA-256 Tamper-Evident Audit Trail (`/audit`)
- **Access:** `ROLE_ANALYST`, `ROLE_APPROVER`, `ROLE_ADMIN`
- **Endpoints:**
  - `GET /api/audit`, `GET /api/audit/events`, `GET /api/audit/events/scenario/{scenarioId}`
  - `GET /api/audit/verify` — cryptographic hash-chain verification (`valid`, `totalEvents`, `brokenAtEventId`)
- **UI Features:**
  - Chronological audit timeline (`APPROVAL_CREATED`, `APPROVAL_APPROVED`, `RFQ_ISSUED`, `APPROVAL_AUDITED`, `SAP_PR_PUSHED`, etc.) with `actor`, `beforeHash`, `afterHash`.
  - Scenario filter and live **"Verify SHA-256 Chain"** integrity indicator.
