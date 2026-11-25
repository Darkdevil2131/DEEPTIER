from datetime import datetime, timedelta, timezone
from io import BytesIO

from pypdf import PdfWriter

from tradepilot.document_processing import process_document_bytes
from tradepilot.evaluation import EvaluationRequest, evaluate_decisions


def blank_pdf() -> bytes:
    stream = BytesIO()
    writer = PdfWriter()
    writer.add_blank_page(width=200, height=200)
    writer.write(stream)
    return stream.getvalue()


def test_pdf_processing_hashes_and_never_claims_authenticity():
    result = process_document_bytes(blank_pdf(), "invoice.pdf", "application/pdf")
    assert result["pages"] == 1
    assert len(result["sha256"]) == 64
    assert result["authenticity"]["status"] == "not_verified"
    assert result["authenticity"]["cryptographic_validation_performed"] is False


def test_document_upload_and_background_job(client, headers):
    pdf = blank_pdf()
    response = client.post(
        "/api/v1/documents/extract", headers=headers,
        files={"file": ("invoice.pdf", pdf, "application/pdf")},
    )
    assert response.status_code == 200, response.text
    assert response.json()["sha256"]

    queued = client.post(
        "/api/v1/documents/process", headers=headers,
        files={"file": ("invoice.pdf", pdf, "application/pdf")},
    )
    assert queued.status_code == 202
    job = client.get(f"/api/v1/jobs/{queued.json()['id']}", headers=headers)
    assert job.status_code == 200
    assert job.json()["status"] == "completed"


def test_upload_rejects_unsupported_content(client, headers):
    response = client.post(
        "/api/v1/documents/extract", headers=headers,
        files={"file": ("payload.exe", b"MZ", "application/octet-stream")},
    )
    assert response.status_code == 422


def test_duplicate_commercial_erp_routes_are_unmounted(client, headers):
    """Java dt-backend owns all RFQs, approvals, bookings, and POs; Python must return 404."""
    assert client.post("/api/v1/rfqs", headers=headers, json={}).status_code == 404
    assert client.post("/api/v1/rfqs/RFQ-1/dispatch", headers=headers).status_code == 404
    assert client.post("/api/v1/rfqs/RFQ-1/responses", headers=headers, json={}).status_code == 404
    assert client.post("/api/v1/approvals", headers=headers, json={}).status_code == 404
    assert client.post("/api/v1/bookings", headers=headers, json={}).status_code == 404
    assert client.post("/api/v1/bookings/BK-1/tracking", headers=headers, json={}).status_code == 404
    assert client.post("/api/v1/purchase-orders", headers=headers, json={}).status_code == 404
    assert client.get("/api/v1/commercial/REC-1", headers=headers).status_code == 404
    assert client.post("/api/v1/notifications/NOTIF-1/send", headers=headers).status_code == 404


def _sample_eval_records():
    return [{
        "shipment_id": f"H-{i}", "recommended_mode": "air", "actual_mode": "air",
        "predicted_cost": 100, "actual_cost": 100, "baseline_cost": 120,
        "predicted_hours": 10, "actual_hours": 10, "predicted_risk": 0.1,
        "disruption_occurred": False, "provenance": "SYNTHETIC",
    } for i in range(5)]


def test_role_permissions_are_enforced(client, monkeypatch):
    monkeypatch.setenv("TRADEPILOT_ANALYST_KEY", "analyst-secret")
    analyst = {"Authorization": "Bearer analyst-secret"}
    resp = client.post(
        "/api/v1/evaluations/decision-quality",
        headers=analyst,
        json={"records": _sample_eval_records()},
    )
    assert resp.status_code == 200
    assert client.get("/api/v1/audit", headers=analyst).status_code == 403


def test_audit_metrics_and_backup(client, headers):
    health = client.get("/api/v1/health")
    assert health.headers["x-content-type-options"] == "nosniff"
    assert health.headers["x-frame-options"] == "DENY"
    assert client.get("/health").status_code == 200
    eval_resp = client.post(
        "/api/v1/evaluations/decision-quality",
        headers=headers,
        json={"records": _sample_eval_records()},
    )
    assert eval_resp.status_code == 200
    metrics = client.get("/api/v1/metrics", headers=headers)
    assert metrics.status_code == 200
    assert "tradepilot_http_requests_total" in metrics.text
    audit = client.get("/api/v1/audit", headers=headers)
    assert audit.status_code == 200
    assert any(event["action"] == "evaluation.run" for event in audit.json()["events"])
    backup = client.post("/api/v1/admin/backups", headers=headers)
    assert backup.status_code == 200
    assert backup.json()["status"] == "created"


def test_decision_quality_metrics_and_endpoint(client, headers):
    records = [{
        "shipment_id": f"H-{index}", "recommended_mode": "air", "actual_mode": "air" if index < 4 else "sea",
        "predicted_cost": 100, "actual_cost": 110, "baseline_cost": 130,
        "predicted_hours": 10, "actual_hours": 12, "predicted_risk": .2,
        "disruption_occurred": index == 4, "provenance": "SYNTHETIC",
    } for index in range(5)]
    result = evaluate_decisions(EvaluationRequest(records=records).records)
    assert result["records"] == 5
    assert result["eta"]["mean_absolute_error_hours"] == 2
    assert result["expert_or_actual_mode_agreement"] == .8
    response = client.post("/api/v1/evaluations/decision-quality", headers=headers, json={"records": records})
    assert response.status_code == 200
    assert response.json()["risk_calibration"]["brier_score"] >= 0


def test_postgresql_store_is_selected_from_database_url(monkeypatch):
    from tradepilot import storage

    selected = {}
    monkeypatch.setenv("TRADEPILOT_DATABASE_URL", "postgresql://example.invalid/tradepilot")
    monkeypatch.setattr(storage, "PostgresShipmentStore", lambda url: selected.setdefault("url", url) or object())
    result = storage.create_shipment_store()
    assert result == "postgresql://example.invalid/tradepilot"
    assert selected["url"].startswith("postgresql://")
