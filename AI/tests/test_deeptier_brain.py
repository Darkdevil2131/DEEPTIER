import json
import httpx

from tradepilot.agents.deeptier_brain import DeepTierBrain
from tradepilot.deeptier_client import DeepTierClient


def _mock_java_transport(captured_calls: list[tuple[str, str, dict | None]]) -> httpx.MockTransport:
    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content.decode()) if request.content else None
        captured_calls.append((request.method, request.url.path, body))
        assert request.headers.get("X-Internal-Key") == "java-internal-secret"

        if request.url.path == "/internal/tools/get_tier_map":
            return httpx.Response(
                200,
                json={
                    "tool": "get_tier_map",
                    "nodes": [
                        {"nodeId": "NODE-OEM-HPH", "tier": 0, "name": "Indian OEM (HPH-1042)", "country": "IN"},
                        {"nodeId": "NODE-T1-ALPHA", "tier": 1, "name": "Alpha Machining", "country": "IN"},
                        {"nodeId": "NODE-T2-BETA", "tier": 2, "name": "Beta Foundry", "country": "CN"},
                    ],
                    "links": [
                        {
                            "fromNodeId": "NODE-T1-ALPHA",
                            "toNodeId": "NODE-T2-BETA",
                            "hsCode": "7325",
                            "annualSpendInr": 4800000,
                            "costFraction": 0.60,
                        }
                    ],
                    "dataClass": "SYNTHETIC",
                },
            )
        if request.url.path == "/internal/tools/compute_exposure":
            return httpx.Response(
                200,
                json={
                    "tool": "compute_exposure",
                    "exposureInr": 576000.00,
                    "dataClass": "SYNTHETIC",
                    "evidenceIds": body.get("evidenceIds", []),
                    "formula": "annualSpendInr * costFraction * deltaPercentagePoints / 100",
                },
            )
        if request.url.path == "/internal/tools/compute_cover":
            return httpx.Response(
                200,
                json={
                    "tool": "compute_cover",
                    "unitsOnHand": 300,
                    "dailyDemand": 20,
                    "daysOfCover": 15.0,
                    "leadTimeDays": body["leadTimeDays"],
                    "gapDays": max(0, body["leadTimeDays"] - 15),
                    "dataClass": "SYNTHETIC",
                },
            )
        if request.url.path == "/api/whatif/simulate":
            return httpx.Response(
                200,
                json={
                    "scenarioId": body["scenarioId"],
                    "hsCode": body["hsCode"],
                    "recoveryOptions": [
                        {"optionId": "OPT-ALT-VN", "action": "Shift 50% volume to Vietnam Tier-2", "netSavingInr": 210000}
                    ],
                },
            )
        return httpx.Response(404, json={"detail": "Not found"})

    return httpx.MockTransport(handler)


def test_deeptier_analyze_with_mocked_java_and_internal_key(client, monkeypatch):
    from tradepilot import main

    captured: list[tuple[str, str, dict | None]] = []
    monkeypatch.setenv("DEEPTIER_INTERNAL_KEY", "java-internal-secret")
    monkeypatch.setattr(
        main,
        "deeptier_brain",
        DeepTierBrain(client_factory=lambda: DeepTierClient(transport=_mock_java_transport(captured))),
    )

    resp = client.post(
        "/api/v1/deeptier/analyze",
        headers={"X-Internal-Key": "java-internal-secret"},
        json={
            "alertText": "China imposes 20% tariff hike on HS 7325 iron castings with 14 days port delay",
            "partCode": "HPH-1042",
            "scenarioId": "SCN-2026-01",
        },
    )
    assert resp.status_code == 200, resp.text
    data = resp.json()

    assert data["scenarioId"] == "SCN-2026-01"
    assert data["partCode"] == "HPH-1042"
    assert data["backendStatus"] == "connected"
    assert data["extracted"]["affectedHsCode"] == "7325"
    assert data["extracted"]["tariffDeltaPercent"] == 20.0
    assert data["extracted"]["delayDays"] == 14
    assert data["extracted"]["extractionEngine"] == "deterministic_regex_fallback"

    # Verify all 4 Java endpoints were called
    paths = [path for _, path, _ in captured]
    assert paths == [
        "/internal/tools/get_tier_map",
        "/internal/tools/compute_exposure",
        "/internal/tools/compute_cover",
        "/api/whatif/simulate",
    ]
    # Delay days (14) + default base lead time (30) = 44
    assert captured[2][2]["leadTimeDays"] == 44

    # Verify grounded brief and approval draft use Java-computed numbers
    assert "NODE-T2-BETA" in data["evidenceIds"]
    assert "576000.0" in data["executiveBrief"]
    assert data["approvalDraft"]["requiresHumanApproval"] is True
    assert data["approvalDraft"]["externalActionPerformed"] is False
    assert data["approvalDraft"]["estimatedExposureInr"] == 576000.0
    assert data["approvalDraft"]["daysOfCover"] == 15.0
    assert data["approvalDraft"]["recommendedAction"]["optionId"] == "OPT-ALT-VN"


def test_deeptier_analyze_accepts_bearer_and_rejects_bad_internal_key(client, headers, monkeypatch):
    from tradepilot import main

    captured: list[tuple[str, str, dict | None]] = []
    monkeypatch.setenv("DEEPTIER_INTERNAL_KEY", "java-internal-secret")
    monkeypatch.setattr(
        main,
        "deeptier_brain",
        DeepTierBrain(client_factory=lambda: DeepTierClient(transport=_mock_java_transport(captured))),
    )

    ok_bearer = client.post(
        "/api/v1/deeptier/analyze",
        headers=headers,
        json={"alertText": "15% tariff on HS 7325", "partCode": "HPH-1042"},
    )
    assert ok_bearer.status_code == 200

    bad_internal = client.post(
        "/api/v1/deeptier/analyze",
        headers={"X-Internal-Key": "wrong-key"},
        json={"alertText": "15% tariff on HS 7325"},
    )
    assert bad_internal.status_code == 401


def test_deeptier_analyze_graceful_when_backend_unreachable(client, headers, monkeypatch):
    from tradepilot import main

    def failing_handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connection refused", request=request)

    monkeypatch.setattr(
        main,
        "deeptier_brain",
        DeepTierBrain(client_factory=lambda: DeepTierClient(transport=httpx.MockTransport(failing_handler))),
    )

    resp = client.post(
        "/api/v1/deeptier/analyze",
        headers=headers,
        json={"alertText": "25% tariff on HS 8708 automotive parts", "partCode": "AXL-200"},
    )
    assert resp.status_code == 200, resp.text
    data = resp.json()
    assert data["backendStatus"] == "unreachable"
    assert data["extracted"]["affectedHsCode"] == "8708"
    assert data["extracted"]["tariffDeltaPercent"] == 25.0
    assert data["approvalDraft"]["estimatedExposureInr"] is None
    assert data["approvalDraft"]["daysOfCover"] is None
    assert len(data["warnings"]) == 4
