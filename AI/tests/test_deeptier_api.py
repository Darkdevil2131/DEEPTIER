from __future__ import annotations

from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient

from tradepilot.main import app


client = TestClient(app)


def test_health_and_ready_endpoints(monkeypatch):
    monkeypatch.setenv("TRADEPILOT_API_KEY", "test-admin-key")
    assert client.get("/api/v1/health").status_code == 200
    assert client.get("/health").status_code == 200
    assert client.get("/api/v1/ready").status_code == 200


def test_duplicate_erp_routes_are_unmounted():
    for method, path in [
        ("POST", "/api/v1/rfqs"),
        ("POST", "/api/v1/rfqs/RFQ-1/dispatch"),
        ("POST", "/api/v1/rfqs/RFQ-1/responses"),
        ("POST", "/api/v1/approvals"),
        ("POST", "/api/v1/bookings"),
        ("POST", "/api/v1/bookings/BK-1/tracking"),
        ("POST", "/api/v1/purchase-orders"),
        ("GET", "/api/v1/commercial/REC-1"),
        ("POST", "/api/v1/notifications/NOTIF-1/send"),
    ]:
        res = client.request(method, path)
        assert res.status_code == 404, f"Expected 404 for {method} {path}, got {res.status_code}"


def test_all_mounted_python_endpoints_covered(monkeypatch):
    monkeypatch.setenv("TRADEPILOT_API_KEY", "test-admin-key")
    monkeypatch.setenv("DEEPTIER_INTERNAL_KEY", "test-internal-key")
    auth = {"Authorization": "Bearer test-admin-key"}

    # 1. OpenAPI schema check
    openapi = client.get("/openapi.json").json()
    assert "/api/v1/deeptier/analyze" in openapi["paths"]
    assert "/api/v1/rfqs" not in openapi["paths"]

    # 2. DeepTier Brain analyze (mocked dt-backend)
    with patch("tradepilot.agents.deeptier_brain.DeepTierClient.get_tier_map", return_value={"nodes": [{"id": "SUP-1"}]}), \
         patch("tradepilot.agents.deeptier_brain.DeepTierClient.compute_exposure", return_value={"exposureInr": 576000}), \
         patch("tradepilot.agents.deeptier_brain.DeepTierClient.compute_cover", return_value={"daysOfCover": 15.0, "gapDays": 15}), \
         patch("tradepilot.agents.deeptier_brain.DeepTierClient.simulate_whatif", return_value={"evidenceIds": ["EV-1"], "recoveryOptions": []}):
        res = client.post(
            "/api/v1/deeptier/analyze",
            headers={"X-Internal-Key": "test-internal-key"},
            json={"alertText": "20% tariff on HS 7325 from China", "partCode": "HPH-1042"},
        )
        assert res.status_code == 200
        assert res.json()["backendStatus"] == "connected"

    # 3. Shipments analyze, get, history, 404 & 422
    import json
    sample = json.loads(Path("examples/analyze_shipment.json").read_text(encoding="utf-8"))
    r_ship = client.post("/api/v1/shipments/analyze", headers=auth, json=sample)
    assert r_ship.status_code == 200
    ship_id = r_ship.json()["shipment_id"]
    assert client.get(f"/api/v1/shipments/{ship_id}", headers=auth).status_code == 200
    assert client.get(f"/api/v1/shipments/{ship_id}/history", headers=auth).status_code == 200
    assert client.get("/api/v1/shipments/NO_SUCH_ID", headers=auth).status_code == 404
    assert client.post("/api/v1/shipments/analyze", headers=auth, json={}).status_code == 422

    # 4. Documents validate, extract, process, job polling
    doc_req = json.loads(Path("examples/document_validation.json").read_text(encoding="utf-8"))
    assert client.post("/api/v1/documents/validate", headers=auth, json=doc_req).status_code == 200
    r_ext = client.post("/api/v1/documents/extract", headers=auth,
                        files={"file": ("note.txt", b"Invoice #101 HS 7325", "text/plain")})
    assert r_ext.status_code == 422  # unsupported text/plain extension

    r_proc = client.post("/api/v1/documents/process", headers=auth,
                         files={"file": ("blank.png", b"\x89PNG\r\n\x1a\n", "image/png")})
    assert r_proc.status_code == 202
    job_id = r_proc.json()["id"]
    assert client.get(f"/api/v1/jobs/{job_id}", headers=auth).status_code == 200
    assert client.get("/api/v1/jobs/MISSING_JOB", headers=auth).status_code == 404

    # 5. Evaluations, Audit, Metrics, Backups
    eval_records = [
        {
            "shipment_id": f"HIST-{i}",
            "recommended_mode": "air",
            "actual_mode": "air",
            "predicted_cost": 1000.0,
            "actual_cost": 1050.0,
            "baseline_cost": 1300.0,
            "predicted_hours": 12.0,
            "actual_hours": 13.0,
            "predicted_risk": 0.2,
            "disruption_occurred": False,
            "provenance": "HISTORICAL",
        }
        for i in range(5)
    ]
    assert client.post("/api/v1/evaluations/decision-quality", headers=auth, json={"records": []}).status_code == 422
    assert client.post("/api/v1/evaluations/decision-quality", headers=auth, json={"records": eval_records}).status_code == 200
    assert client.get("/api/v1/audit", headers=auth).status_code == 200
    assert client.get("/api/v1/metrics", headers=auth).status_code == 200
    assert client.post("/api/v1/admin/backups", headers=auth).status_code == 200
