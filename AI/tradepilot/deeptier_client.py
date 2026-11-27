"""HTTP client for calling deterministic math and governance tools in Java dt-backend."""
from __future__ import annotations

import os
from typing import Any

import httpx


class DeepTierClientError(RuntimeError):
    """Raised when dt-backend is unreachable or returns an unexpected error."""

    def __init__(self, message: str, status_code: int | None = None):
        super().__init__(message)
        self.status_code = status_code


class DeepTierClient:
    """Thin httpx client for Java dt-backend /internal/tools/* and /api/whatif/simulate."""

    def __init__(
        self,
        base_url: str | None = None,
        internal_key: str | None = None,
        timeout: float = 4.0,
        transport: httpx.BaseTransport | None = None,
    ):
        self.base_url = (base_url or os.getenv("DEEPTIER_BACKEND_URL") or "http://127.0.0.1:8080").rstrip("/")
        self.internal_key = internal_key if internal_key is not None else os.getenv("DEEPTIER_INTERNAL_KEY", "")
        self.timeout = timeout
        self.transport = transport

    def _headers(self) -> dict[str, str]:
        headers = {"Accept": "application/json"}
        if self.internal_key:
            headers["X-Internal-Key"] = self.internal_key
        return headers

    def _request(self, method: str, path: str, json_body: dict[str, Any] | None = None) -> dict[str, Any]:
        try:
            with httpx.Client(
                base_url=self.base_url,
                headers=self._headers(),
                timeout=self.timeout,
                transport=self.transport,
            ) as client:
                response = client.request(method, path, json=json_body)
        except httpx.HTTPError as exc:
            raise DeepTierClientError(f"dt-backend request failed for {method} {path}: {type(exc).__name__}") from exc

        if response.status_code >= 400:
            raise DeepTierClientError(
                f"dt-backend returned {response.status_code} for {method} {path}",
                status_code=response.status_code,
            )
        try:
            data = response.json()
        except ValueError as exc:
            raise DeepTierClientError(f"dt-backend returned invalid JSON for {method} {path}") from exc
        if not isinstance(data, dict):
            raise DeepTierClientError(f"dt-backend returned non-object JSON for {method} {path}")
        return data

    def get_tier_map(self) -> dict[str, Any]:
        return self._request("GET", "/internal/tools/get_tier_map")

    def compute_exposure(
        self,
        annual_spend_inr: float,
        cost_fraction: float,
        delta_percentage_points: float,
        evidence_ids: list[str] | None = None,
    ) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "annualSpendInr": annual_spend_inr,
            "costFraction": cost_fraction,
            "deltaPercentagePoints": delta_percentage_points,
        }
        if evidence_ids is not None:
            payload["evidenceIds"] = evidence_ids
        return self._request("POST", "/internal/tools/compute_exposure", payload)

    def compute_cover(
        self,
        units_on_hand: int,
        daily_demand: int,
        lead_time_days: int,
    ) -> dict[str, Any]:
        return self._request(
            "POST",
            "/internal/tools/compute_cover",
            {
                "unitsOnHand": units_on_hand,
                "dailyDemand": daily_demand,
                "leadTimeDays": lead_time_days,
            },
        )

    def simulate_whatif(self, payload: dict[str, Any] | None = None) -> dict[str, Any]:
        return self._request("POST", "/api/whatif/simulate", payload or {})
