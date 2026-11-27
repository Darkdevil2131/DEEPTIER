# DEEPTIER Python AI handover

Run the Python service from `AI/` alongside the Java backend. Java is the
frontend entrypoint and owns calculation results, approvals, mock RFQs, and
ERP payloads. Keep Python reachable only by trusted services.

## Local configuration

| Variable | Service | Value |
| --- | --- | --- |
| `DEEPTIER_INTERNAL_KEY` | Java and Python | The same private value in both services |
| `DEEPTIER_BACKEND_URL` | Python | Java's base URL, locally `http://127.0.0.1:8080` |
| `DEEPTIER_PYTHON_URL` | Java | Python's base URL, locally `http://127.0.0.1:8000` |
| `TRADEPILOT_API_KEY` | Python | Optional for Java analysis calls. Required for bearer-protected operations when separate role keys are absent. |
| `GEMINI_ENABLED` | Python | `false` for deterministic extraction and briefs |

From `AI/`, install `requirements.txt` and start
`python -m uvicorn tradepilot.main:app --host 127.0.0.1 --port 8000`.

## Analysis contracts

| Python endpoint | Java caller |
| --- | --- |
| `POST /api/v1/deeptier/analyze` | Send `alertText`, known part/scenario IDs, and any supplied inventory and financial inputs. |
| `POST /api/v1/shipments/analyze` | Send the shipment record. A sample is in `examples/analyze_shipment.json`. |

Send `X-Internal-Key` on both calls. Existing trusted callers can use an
analyst bearer key. Keep both credentials off the frontend. The shipment
endpoint can receive the proposed Java `/api/logistics/reroute` proxy request
once that Java endpoint is wired.

Python uses Java's tier map, duty exposure, cover and what-if tools. It reads
`dutyExposureInr`, supports both tier-link naming conventions, and sends
fractional tariff changes without rounding. The Java DTOs must accept decimal
values to preserve them end to end. The Python tool phase allows up to six
seconds in aggregate with a four-second HTTP timeout per tool. Optional model
calls have separate latency, so use deterministic mode for the short Java
gateway deadline.

## Cloud Foundry

The `manifest.yml` defines a 512 MB Python app and probes
`/api/v1/health`. Its internal route assumes the foundation supplies
`apps.internal`. Configure the Java-to-Python network policy and Java callback
address for the target space. The manifest contains no secrets. The liveness
probe does not verify storage or Java connectivity.

Run `python -m pytest` from `AI/`. See the [API guide](API_INTEGRATION_GUIDE.md)
for mounted Python routes and examples.
