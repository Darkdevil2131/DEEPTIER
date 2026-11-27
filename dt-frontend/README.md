# DEEPTIER — Connected Trade Intelligence
Desktop-first React/Vite frontend, independent SAP Fiori-inspired visual identity. No backend changes.

## Run
From this directory, use Node 20+:
```
npm install
npm test
npm run dev
```
Open the Vite URL. The development proxy forwards /api to http://127.0.0.1:8080 (override DEEPTIER_API_TARGET in a local .env). In production, configure a same-origin reverse proxy; do not expose Python or private service keys. Run npm run build for a static bundle.

## Implemented scope
Landing illustration of all four transport modes, disconnected enterprise login/explicit unauthenticated preview, shipment request form, optional emergency split inputs, structured carrier/document/disruption evidence, explicit backend demo defaults, actual returned route-leg schematic, route alternatives, feasibility/decision gate distinction, per-leg inspector, read-only supplier records, disruption and what-if API forms, read-only approvals/RFQ/SAP payload/audit operations. Keyword routing suggests relevant workspaces; it is not AI. The JSON inspector preserves the received response.

## Truthful limits
No live AIS, ADS-B, train or truck telemetry in the Java API; no invented vehicle locations, geographic geometry, vendor associations, quotes or profits. The chain is a schematic of returned legs and does not assert continuity when endpoints disagree. Backend responses may be synthetic or incomplete; UI preserves provenance and warnings. No actual authentication, approval submission, SAP posting, unified scenario branching, interactive geographic map, independent document processing, full supplier relationship graph or live agent traces. No new backend endpoints. The other workspaces are initial data/contract views, not complete production modules.

## Verification
Unit test source is provided for validation, gate handling, chain continuity and query suggestions. Install and run tests/build in a normal Node environment, then verify the landing/login and API paths in a browser with the running Java service. This change has not been browser-tested or built in the connector session. No credentials or paid services required for frontend development.
