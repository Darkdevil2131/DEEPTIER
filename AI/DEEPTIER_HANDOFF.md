# TradePilot integration for DEEPTIER

This directory starts from `Darkdevil2131/tradepilot-ai-agents` at `00b056b89fed89d765ebcf85fad49a738a0d9a76` and adds domestic freight planning over a 57-hub road graph, including Kochi–Chennai and other inland corridors. Run the Python service from `AI/` as a separate process alongside the Spring Boot service in `dt-backend/`.

## Run locally

From the DEEPTIER repository root:

```sh
cd AI
python -m venv .venv
. .venv/bin/activate
python -m pip install -r requirements.txt
cp .env.example .env
```

Set a private `TRADEPILOT_API_KEY` in `AI/.env`. Keep `GEMINI_ENABLED=false` for offline development. Start the service with `python -m uvicorn tradepilot.main:app --host 127.0.0.1 --port 8000`. A local OCR installation needs Tesseract. The included Dockerfile installs it.

## Connect the Java backend

The website backend can call `POST /internal/tools/tradepilot/shipments/analyze` on DEEPTIER's Java service. Send the TradePilot shipment JSON and DEEPTIER's existing `X-Internal-Key` header. Keep this request server-side. Java forwards the shipment to the six-agent workflow and returns its response, including the decision, gate reasons, alternatives and evidence. The existing tariff-signal and what-if functions use a separate contract.

Set these environment variables on the Java service.

| Variable | Purpose |
| --- | --- |
| `DEEPTIER_PYTHON_URL` | TradePilot base URL. Defaults to `http://127.0.0.1:8000` for local development. |
| `TRADEPILOT_API_KEY` | TradePilot bearer credential with analyst access. Keep it in the backend environment. |
| `DEEPTIER_TRADEPILOT_TIMEOUT_SECONDS` | Request deadline including the response body. Defaults to 30 seconds. |

The Java endpoint returns 401 for missing or incorrect internal authentication, 400 for malformed JSON, 422 for an invalid shipment, 429 when the upstream quota is reached, 502 for unexpected upstream responses, 503 when the service cannot be reached or used, and 504 when the gateway deadline expires. On a timeout, the Python service may still complete the original analysis. Avoid automatically resubmitting the same request.

Use `examples/analyze_shipment.json` for a first request and `GET /api/v1/health` for a liveness check. Inspect the `agents` and `data_provenance` fields before displaying recommendations. See [the API integration guide](API_INTEGRATION_GUIDE.md) for details.

Run `python -m pytest` from `AI/`. The copied GitHub workflows are included for reference. GitHub Actions only discovers workflows in the repository-root `.github/workflows/` directory.

Run `./mvnw -Dtest=TradePilotControllerTest test` from `dt-backend/` to check the Java adapter. With a local TradePilot process running, set `TRADEPILOT_TEST_URL` to its base URL and `TRADEPILOT_TEST_API_KEY` to its test credential before running Maven. The end-to-end test submits a 500 kg Kochi–Chennai shipment through Spring MVC, then closes the destination port and verifies the agents switch the remainder of the cargo from modeled ocean freight to rail.
