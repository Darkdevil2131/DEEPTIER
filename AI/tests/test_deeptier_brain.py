import json
from time import monotonic
import httpx
import pytest
from threading import Event
from types import SimpleNamespace

from tradepilot.agents.deeptier_brain import DeepTierAnalyzeRequest, DeepTierBrain, _extract_with_gemini
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
                            "from": "NODE-T1-ALPHA",
                            "to": "NODE-T2-BETA",
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
                    "dutyExposureInr": 576000.00,
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
                    "daysOfCoverDisplay": "15.0",
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
    paths = {path: payload for _, path, payload in captured}
    assert paths.keys() == {
        "/internal/tools/get_tier_map",
        "/internal/tools/compute_exposure",
        "/internal/tools/compute_cover",
        "/api/whatif/simulate",
    }
    # Delay days (14) + default base lead time (30) = 44
    assert paths["/internal/tools/compute_cover"]["leadTimeDays"] == 44

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


@pytest.mark.parametrize(("alert", "delta", "signal"), [
    ("Duty rises from 10% to 30% on HSN 7325 from India", 20.0, "TARIFF_SHOCK"),
    ("12.5% tariff on HSN 7325 from India", 12.5, "TARIFF_SHOCK"),
    ("0% tariff on HSN 7325 from India", 0.0, "TARIFF_SHOCK"),
    ("HSN 7325 delayed by 4 days at port", 0.0, "LOGISTICS_DISRUPTION"),
])
def test_alert_shapes_reach_java_without_invented_tariffs(monkeypatch, alert, delta, signal):
    monkeypatch.setenv("GEMINI_ENABLED", "false")
    captured = []
    brain = DeepTierBrain(lambda: DeepTierClient(
        internal_key="java-internal-secret", transport=_mock_java_transport(captured)))
    result = brain.analyze(DeepTierAnalyzeRequest(alertText=alert))
    assert result["extracted"]["affectedHsCode"] == "7325"
    assert result["extracted"]["tariffDeltaPercent"] == delta
    assert result["extracted"]["signalType"] == signal
    assert result["backendStatus"] == "connected"
    paths = {path: payload for _, path, payload in captured}
    assert paths["/api/whatif/simulate"]["deltaPercentagePoints"] == delta
    if delta == 0.0:
        assert "/internal/tools/compute_exposure" not in paths
        assert result["approvalDraft"]["estimatedExposureInr"] == 0.0
    else:
        assert paths["/internal/tools/compute_exposure"]["deltaPercentagePoints"] == delta


def test_unparsed_alert_requires_review_and_skips_java():
    def unexpected_java_client():
        pytest.fail("Unparsed alerts must not call Java tools")

    result = DeepTierBrain(unexpected_java_client).analyze(DeepTierAnalyzeRequest(alertText="Please see attached."))
    assert result["extracted"]["signalType"] == "UNPARSED"
    assert result["extracted"]["affectedHsCode"] == "UNKNOWN"
    assert result["extracted"]["tariffDeltaPercent"] == 0.0
    assert result["extracted"]["confidence"] == 0.25
    assert result["backendStatus"] == "skipped"
    assert "Human review is required" in result["executiveBrief"]
    assert result["approvalDraft"]["recommendedAction"] == "HUMAN_REVIEW_REQUIRED"


def test_gemini_preserves_zero_tariff(monkeypatch):
    from google import genai

    monkeypatch.setenv("GEMINI_ENABLED", "true")
    monkeypatch.setenv("GEMINI_API_KEY", "local-test-placeholder")
    response = SimpleNamespace(text=json.dumps({"affectedHsCode": "7325", "tariffDeltaPercent": 0}))
    monkeypatch.setattr(genai, "Client", lambda **_: SimpleNamespace(
        models=SimpleNamespace(generate_content=lambda **_: response)))
    assert _extract_with_gemini("0% tariff on HSN 7325", "HPH-1042")["tariffDeltaPercent"] == 0.0


def test_cover_and_exposure_overlap():
    cover_started, exposure_started = Event(), Event()

    class JavaFixture:
        def get_tier_map(self):
            return {"links": [{"fromNodeId": "A", "toNodeId": "B", "hsCode": "7325"}]}

        def compute_cover(self, *_):
            cover_started.set()
            assert exposure_started.wait(2)
            return {"daysOfCover": 15}

        def compute_exposure(self, **_):
            exposure_started.set()
            assert cover_started.wait(2)
            return {"exposureInr": 576000}

        def simulate_whatif(self, payload):
            return {"scenarioId": payload["scenarioId"]}

    result = DeepTierBrain(JavaFixture).analyze(DeepTierAnalyzeRequest(alertText="20% tariff on HSN 7325"))
    assert result["backendStatus"] == "connected"
    assert result["evidenceIds"] == ["A", "B", "HS-7325"]
    assert result["approvalDraft"]["estimatedExposureInr"] == 576000


def test_slow_java_tool_yields_partial_result_within_budget(monkeypatch):
    from tradepilot.agents import deeptier_brain

    monkeypatch.setattr(deeptier_brain, "JAVA_TOOL_BUDGET_SECONDS", .1)
    release = Event()

    class JavaFixture:
        def get_tier_map(self):
            return {}

        def compute_cover(self, *_):
            release.wait(3)
            return {"daysOfCover": 15}

        def simulate_whatif(self, _):
            return {"recoveryOptions": []}

    started = monotonic()
    try:
        result = DeepTierBrain(JavaFixture).analyze(DeepTierAnalyzeRequest(alertText="Port closure"))
    finally:
        release.set()
    assert monotonic() - started < 2
    assert result["backendStatus"] == "partial"
    assert result["deterministicMath"]["cover"] is None
    assert any("compute_cover" in warning and "budget exceeded" in warning for warning in result["warnings"])


def test_customs_delay_classifies_as_logistics_and_extracts_delay():
    from tradepilot.agents.deeptier_brain import _extract_deterministic

    extracted = _extract_deterministic("Customs delay of 3 days at Chennai port")
    assert extracted["signalType"] == "LOGISTICS_DISRUPTION"
    assert extracted["delayDays"] == 3
    assert extracted["tariffDeltaPercent"] == 0.0

    # "customs" alone without rate or logistics evidence must not trigger TARIFF_SHOCK
    bare_customs = _extract_deterministic("Customs inspection notice")
    assert bare_customs["signalType"] == "UNPARSED"
