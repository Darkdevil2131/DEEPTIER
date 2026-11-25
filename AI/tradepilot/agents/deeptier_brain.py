"""DeepTier LangGraph reasoning brain.

Extracts structured disruption parameters from alerts, delegates ALL deterministic
financial/inventory/Monte-Carlo arithmetic to Java dt-backend via DeepTierClient,
and synthesizes a grounded executiveBrief + approvalDraft for human-in-the-loop review.
"""
from __future__ import annotations

import json
import os
import re
from typing import Any, TypedDict
from uuid import uuid4

from langgraph.graph import END, START, StateGraph
from pydantic import BaseModel, ConfigDict, Field

from ..deeptier_client import DeepTierClient, DeepTierClientError


class DeepTierAnalyzeRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")

    alertText: str = Field(default="", max_length=20_000)
    partCode: str = Field(default="HPH-1042", min_length=1, max_length=80)
    scenarioId: str | None = Field(default=None, max_length=80)
    unitsOnHand: int | None = Field(default=None, ge=0, le=1_000_000_000)
    dailyDemand: int | None = Field(default=None, ge=0, le=1_000_000_000)
    leadTimeDays: int | None = Field(default=None, ge=0, le=3650)
    annualSpendInr: float | None = Field(default=None, ge=0, le=1e14)
    costFraction: float | None = Field(default=None, ge=0, le=1)
    lateCostPerDayInr: float | None = Field(default=None, ge=0, le=1e12)


class DeepTierBrainState(TypedDict, total=False):
    request: DeepTierAnalyzeRequest
    scenario_id: str
    extracted: dict[str, Any]
    tier_map: dict[str, Any] | None
    exposure: dict[str, Any] | None
    cover: dict[str, Any] | None
    whatif: dict[str, Any] | None
    evidence_ids: list[str]
    warnings: list[str]
    backend_status: str
    executive_brief: str
    approval_draft: dict[str, Any]


_HS_PATTERN = re.compile(r"\bHS(?:\s*code)?[\s:#-]*(\d{4,8})\b", re.IGNORECASE)
_PCT_PATTERN = re.compile(r"(\d+(?:\.\d+)?)\s*(?:%|percent|percentage\s*points?)", re.IGNORECASE)
_DELAY_PATTERN = re.compile(
    r"(?:delay(?:ed)?(?:\s+by)?\s*|lead\s*time\s*\+?\s*)(\d+)\s*days?\b|\b(\d+)\s*days?\s*(?:[a-z]+\s+){0,2}(?:delay|stoppage|lead)",
    re.IGNORECASE,
)


def _extract_deterministic(alert_text: str) -> dict[str, Any]:
    text = alert_text or ""
    hs_match = _HS_PATTERN.search(text)
    pct_match = _PCT_PATTERN.search(text)
    delay_match = _DELAY_PATTERN.search(text)

    affected_hs = hs_match.group(1) if hs_match else "7325"
    tariff_delta = float(pct_match.group(1)) if pct_match else 20.0
    delay_days = int(delay_match.group(1) or delay_match.group(2)) if delay_match else 0

    lowered = text.casefold()
    origin_country = "CN"
    if "vietnam" in lowered:
        origin_country = "VN"
    elif "germany" in lowered:
        origin_country = "DE"
    elif "japan" in lowered:
        origin_country = "JP"
    elif "india" in lowered and "china" not in lowered:
        origin_country = "IN"

    signal_type = "TARIFF_SHOCK"
    if any(k in lowered for k in ("port", "congestion", "strike", "blockade", "delay")) and not pct_match:
        signal_type = "LOGISTICS_DISRUPTION"

    confidence = 0.85 if (hs_match or pct_match) else 0.55
    return {
        "affectedHsCode": affected_hs,
        "originCountry": origin_country,
        "tariffDeltaPercent": tariff_delta,
        "delayDays": delay_days,
        "signalType": signal_type,
        "confidence": confidence,
        "extractionEngine": "deterministic_regex_fallback",
    }


def _extract_with_gemini(alert_text: str, part_code: str) -> dict[str, Any] | None:
    if os.getenv("GEMINI_ENABLED", "false").strip().lower() != "true":
        return None
    api_key = os.getenv("GEMINI_API_KEY", "").strip()
    if not api_key:
        return None
    model = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
    try:
        from google import genai
        from google.genai import types

        client = genai.Client(
            api_key=api_key,
            http_options=types.HttpOptions(timeout=int(os.getenv("GEMINI_TIMEOUT_MS", "5000"))),
        )
        prompt = (
            "Extract structured disruption parameters from the supply chain alert. "
            "Return ONLY a JSON object with keys: affectedHsCode (string, 4-8 digits), "
            "originCountry (2-letter ISO code), tariffDeltaPercent (number), delayDays (integer), "
            "signalType (string, e.g. TARIFF_SHOCK or LOGISTICS_DISRUPTION), confidence (0..1 float). "
            f"PartCode={part_code}\nAlert={alert_text}"
        )
        response = client.models.generate_content(
            model=model,
            contents=prompt,
            config=types.GenerateContentConfig(temperature=0, max_output_tokens=200),
        )
        raw = (response.text or "").strip()
        if raw.startswith("```"):
            raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", raw, flags=re.DOTALL).strip()
        parsed = json.loads(raw)
        if not isinstance(parsed, dict):
            return None
        return {
            "affectedHsCode": str(parsed.get("affectedHsCode") or "7325"),
            "originCountry": str(parsed.get("originCountry") or "CN")[:2].upper(),
            "tariffDeltaPercent": float(parsed.get("tariffDeltaPercent") or 20.0),
            "delayDays": int(parsed.get("delayDays") or 0),
            "signalType": str(parsed.get("signalType") or "TARIFF_SHOCK"),
            "confidence": float(parsed.get("confidence") or 0.8),
            "extractionEngine": f"gemini:{model}",
        }
    except Exception:
        return None


def _synthesize_brief_with_gemini(payload: dict[str, Any]) -> str | None:
    if os.getenv("GEMINI_ENABLED", "false").strip().lower() != "true":
        return None
    api_key = os.getenv("GEMINI_API_KEY", "").strip()
    if not api_key:
        return None
    model = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
    try:
        from google import genai
        from google.genai import types

        client = genai.Client(
            api_key=api_key,
            http_options=types.HttpOptions(timeout=int(os.getenv("GEMINI_TIMEOUT_MS", "5000"))),
        )
        prompt = (
            "Write a concise 3-sentence executive brief using ONLY the supplied deterministic JSON. "
            "Do not invent any numbers or IDs. Cite exact evidenceIds, exposureInr, daysOfCover, and state "
            "that human approval is required in dt-backend before execution.\nJSON="
            + json.dumps(payload, default=str)
        )
        response = client.models.generate_content(
            model=model,
            contents=prompt,
            config=types.GenerateContentConfig(temperature=0, max_output_tokens=220),
        )
        text = (response.text or "").strip()
        return text or None
    except Exception:
        return None


class DeepTierBrain:
    """LangGraph workflow orchestrating signal extraction -> Java deterministic math -> brief synthesis."""

    def __init__(self, client_factory=None):
        self.client_factory = client_factory or DeepTierClient
        builder = StateGraph(DeepTierBrainState)
        builder.add_node("extract_disruption", self._extract_disruption)
        builder.add_node("fetch_deterministic_math", self._fetch_deterministic_math)
        builder.add_node("synthesize_brief", self._synthesize_brief)
        builder.add_edge(START, "extract_disruption")
        builder.add_edge("extract_disruption", "fetch_deterministic_math")
        builder.add_edge("fetch_deterministic_math", "synthesize_brief")
        builder.add_edge("synthesize_brief", END)
        self.graph = builder.compile()

    def _extract_disruption(self, state: DeepTierBrainState) -> dict[str, Any]:
        req = state["request"]
        extracted = _extract_with_gemini(req.alertText, req.partCode)
        if extracted is None:
            extracted = _extract_deterministic(req.alertText)
        return {"extracted": extracted}

    def _fetch_deterministic_math(self, state: DeepTierBrainState) -> dict[str, Any]:
        req = state["request"]
        extracted = state["extracted"]
        client: DeepTierClient = self.client_factory()
        warnings: list[str] = []
        evidence_ids: list[str] = []

        tier_map: dict[str, Any] | None = None
        exposure: dict[str, Any] | None = None
        cover: dict[str, Any] | None = None
        whatif: dict[str, Any] | None = None

        annual_spend = req.annualSpendInr if req.annualSpendInr is not None else 4_800_000.0
        cost_fraction = req.costFraction if req.costFraction is not None else 0.60
        units_on_hand = req.unitsOnHand if req.unitsOnHand is not None else 300
        daily_demand = req.dailyDemand if req.dailyDemand is not None else 20
        base_lead = req.leadTimeDays if req.leadTimeDays is not None else 30
        effective_lead = base_lead + int(extracted.get("delayDays") or 0)
        late_cost = req.lateCostPerDayInr if req.lateCostPerDayInr is not None else 80_000.0

        calls_attempted = 4
        calls_succeeded = 0

        try:
            tier_map = client.get_tier_map()
            calls_succeeded += 1
            for node in tier_map.get("nodes") or []:
                if isinstance(node, dict) and node.get("nodeId"):
                    evidence_ids.append(str(node["nodeId"]))
            for link in tier_map.get("links") or []:
                if isinstance(link, dict) and link.get("hsCode") == extracted["affectedHsCode"]:
                    if req.annualSpendInr is None and link.get("annualSpendInr") is not None:
                        annual_spend = float(link["annualSpendInr"])
                    if req.costFraction is None and link.get("costFraction") is not None:
                        cost_fraction = float(link["costFraction"])
            evidence_ids.append(f"HS-{extracted['affectedHsCode']}")
        except DeepTierClientError as exc:
            warnings.append(f"get_tier_map unavailable: {exc}")

        try:
            exposure = client.compute_exposure(
                annual_spend_inr=annual_spend,
                cost_fraction=cost_fraction,
                delta_percentage_points=float(extracted["tariffDeltaPercent"]),
                evidence_ids=evidence_ids or [f"HS-{extracted['affectedHsCode']}"],
            )
            calls_succeeded += 1
            for eid in exposure.get("evidenceIds") or []:
                if str(eid) not in evidence_ids:
                    evidence_ids.append(str(eid))
        except DeepTierClientError as exc:
            warnings.append(f"compute_exposure unavailable: {exc}")

        try:
            cover = client.compute_cover(
                units_on_hand=units_on_hand,
                daily_demand=daily_demand,
                lead_time_days=effective_lead,
            )
            calls_succeeded += 1
        except DeepTierClientError as exc:
            warnings.append(f"compute_cover unavailable: {exc}")

        try:
            whatif = client.simulate_whatif(
                {
                    "scenarioId": state["scenario_id"],
                    "hsCode": extracted["affectedHsCode"],
                    "originCountry": extracted["originCountry"],
                    "deltaPercentagePoints": int(round(float(extracted["tariffDeltaPercent"]))),
                    "annualSpendInr": annual_spend,
                    "costFraction": cost_fraction,
                    "unitsOnHand": units_on_hand,
                    "dailyDemand": daily_demand,
                    "leadTimeDays": effective_lead,
                    "lateCostPerDayInr": late_cost,
                    "seed": 20261121,
                }
            )
            calls_succeeded += 1
        except DeepTierClientError as exc:
            warnings.append(f"simulate_whatif unavailable: {exc}")

        if calls_succeeded == calls_attempted:
            backend_status = "connected"
        elif calls_succeeded > 0:
            backend_status = "partial"
        else:
            backend_status = "unreachable"

        return {
            "tier_map": tier_map,
            "exposure": exposure,
            "cover": cover,
            "whatif": whatif,
            "evidence_ids": evidence_ids,
            "warnings": warnings,
            "backend_status": backend_status,
        }

    def _synthesize_brief(self, state: DeepTierBrainState) -> dict[str, Any]:
        req = state["request"]
        extracted = state["extracted"]
        exposure = state.get("exposure") or {}
        cover = state.get("cover") or {}
        whatif = state.get("whatif") or {}
        evidence_ids = state.get("evidence_ids") or []
        backend_status = state.get("backend_status", "unreachable")

        exposure_inr = exposure.get("exposureInr")
        days_of_cover = cover.get("daysOfCover")
        gap_days = cover.get("gapDays")
        recovery_options = whatif.get("recoveryOptions") or whatif.get("options") or []
        top_option = recovery_options[0] if isinstance(recovery_options, list) and recovery_options else None

        brief_payload = {
            "partCode": req.partCode,
            "scenarioId": state["scenario_id"],
            "backendStatus": backend_status,
            "extracted": extracted,
            "exposureInr": exposure_inr,
            "daysOfCover": days_of_cover,
            "gapDays": gap_days,
            "topRecoveryOption": top_option,
            "evidenceIds": evidence_ids,
        }

        executive_brief = _synthesize_brief_with_gemini(brief_payload)
        if not executive_brief:
            if backend_status == "unreachable":
                executive_brief = (
                    f"Extracted {extracted['signalType']} for part {req.partCode} "
                    f"(HS {extracted['affectedHsCode']}, +{extracted['tariffDeltaPercent']}% tariff delta, "
                    f"{extracted['delayDays']}d delay) via {extracted['extractionEngine']}. "
                    f"Deterministic math is pending because dt-backend was unreachable; "
                    f"no financial or inventory figures were computed in Python. "
                    f"Human review is required in dt-backend before any commercial action."
                )
            else:
                ev_str = ", ".join(evidence_ids) if evidence_ids else "none"
                executive_brief = (
                    f"Part {req.partCode} (HS {extracted['affectedHsCode']}, origin {extracted['originCountry']}) "
                    f"faces a {extracted['signalType']} (+{extracted['tariffDeltaPercent']}% tariff delta, "
                    f"{extracted['delayDays']}d delay) with dt-backend deterministic duty exposure of "
                    f"INR {exposure_inr} and {days_of_cover} days of cover (gap {gap_days} days). "
                    f"Grounded in evidenceIds [{ev_str}]. "
                    f"Draft prepared for human approval in dt-backend; no external PO/RFQ was executed."
                )

        approval_draft = {
            "status": "DRAFT_PENDING_HUMAN_APPROVAL",
            "requiresHumanApproval": True,
            "externalActionPerformed": False,
            "partCode": req.partCode,
            "scenarioId": state["scenario_id"],
            "affectedHsCode": extracted["affectedHsCode"],
            "originCountry": extracted["originCountry"],
            "estimatedExposureInr": exposure_inr,
            "daysOfCover": days_of_cover,
            "gapDays": gap_days,
            "recommendedAction": top_option if top_option is not None else "HUMAN_REVIEW_REQUIRED",
            "evidenceIds": evidence_ids,
        }
        return {
            "executive_brief": executive_brief,
            "approval_draft": approval_draft,
        }

    def analyze(self, request: DeepTierAnalyzeRequest) -> dict[str, Any]:
        scenario_id = request.scenarioId or f"DT-{uuid4().hex[:10].upper()}"
        final_state = self.graph.invoke(
            {
                "request": request,
                "scenario_id": scenario_id,
            }
        )
        return {
            "scenarioId": scenario_id,
            "partCode": request.partCode,
            "backendStatus": final_state.get("backend_status", "unreachable"),
            "extracted": final_state.get("extracted", {}),
            "deterministicMath": {
                "tierMap": final_state.get("tier_map"),
                "exposure": final_state.get("exposure"),
                "cover": final_state.get("cover"),
                "whatIf": final_state.get("whatif"),
            },
            "evidenceIds": final_state.get("evidence_ids", []),
            "executiveBrief": final_state.get("executive_brief", ""),
            "approvalDraft": final_state.get("approval_draft", {}),
            "warnings": final_state.get("warnings", []),
        }
