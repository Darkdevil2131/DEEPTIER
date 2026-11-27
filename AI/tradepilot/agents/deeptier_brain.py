"""DeepTier LangGraph reasoning brain.

Extracts structured disruption parameters from alerts, delegates ALL deterministic
financial/inventory/Monte-Carlo arithmetic to Java dt-backend via DeepTierClient,
and synthesizes a grounded executiveBrief + approvalDraft for human-in-the-loop review.
"""
from __future__ import annotations

import json
import logging
import os
import re

logger = logging.getLogger("tradepilot.deeptier_brain")
from concurrent.futures import ThreadPoolExecutor
from time import monotonic
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


_HS_PATTERN = re.compile(r"\bHSN?(?:\s*code)?[\s:#-]*(\d{4,8})\b", re.IGNORECASE)
_PCT_PATTERN = re.compile(r"(\d+(?:\.\d+)?)\s*(?:%|percent|percentage\s*points?)", re.IGNORECASE)
_RANGE_PATTERN = re.compile(r"\bfrom\s+(\d+(?:\.\d+)?)\s*(?:%|percent)\s+to\s+(\d+(?:\.\d+)?)\s*(?:%|percent)", re.IGNORECASE)
_TARIFF_PATTERN = re.compile(r"\b(?:tariffs?|dut(?:y|ies)|levy|customs)\b", re.IGNORECASE)
_DELAY_PATTERN = re.compile(
    r"(?:delay(?:ed)?(?:\s+(?:by|of))?\s*|lead\s*time\s*\+?\s*)(\d+)\s*days?\b|\b(\d+)\s*days?\s*(?:[a-z]+\s+){0,2}(?:delay|stoppage|lead)",
    re.IGNORECASE,
)
_LOGISTICS_PATTERN = re.compile(r"\b(?:congestion|strike|blockade|delay(?:ed|s)?|stoppage|port\s+closure)\b", re.IGNORECASE)
JAVA_TOOL_BUDGET_SECONDS = 6.0


def _extract_deterministic(alert_text: str) -> dict[str, Any]:
    text = alert_text or ""
    hs_match = _HS_PATTERN.search(text)
    pct_match = _PCT_PATTERN.search(text)
    delay_match = _DELAY_PATTERN.search(text)
    tariff = _TARIFF_PATTERN.search(text)
    rate_range = _RANGE_PATTERN.search(text) if tariff or hs_match else None
    affected_hs = hs_match.group(1) if hs_match else "UNKNOWN"
    tariff_delta = (float(rate_range.group(2)) - float(rate_range.group(1)) if rate_range else
                    float(pct_match.group(1)) if pct_match and tariff else 0.0)
    delay_days = int(delay_match.group(1) or delay_match.group(2)) if delay_match else 0

    lowered = text.casefold()
    origin_country = "UNKNOWN"
    if "china" in lowered:
        origin_country = "CN"
    elif "vietnam" in lowered:
        origin_country = "VN"
    elif "germany" in lowered:
        origin_country = "DE"
    elif "japan" in lowered:
        origin_country = "JP"
    elif "india" in lowered and "china" not in lowered:
        origin_country = "IN"

    has_rate = bool(rate_range or pct_match)
    if rate_range or (tariff and has_rate):
        signal_type = "TARIFF_SHOCK"
    elif delay_match or _LOGISTICS_PATTERN.search(text):
        signal_type = "LOGISTICS_DISRUPTION"
    elif hs_match:
        signal_type = "HS_REFERENCE"
    else:
        signal_type = "UNPARSED"

    confidence = 0.25 if signal_type == "UNPARSED" else 0.85 if hs_match or tariff and pct_match else 0.55
    return {
        "affectedHsCode": affected_hs,
        "originCountry": origin_country,
        "tariffDeltaPercent": tariff_delta,
        "delayDays": delay_days,
        "signalType": signal_type,
        "confidence": confidence,
        "extractionEngine": "deterministic_regex_fallback",
    }


def _generate_gemini_content(client: Any, types: Any, model: str, prompt: str) -> Any:
    config_kwargs: dict[str, Any] = {"temperature": 0, "max_output_tokens": 1024}
    if hasattr(types, "ThinkingConfig"):
        try:
            config_kwargs["thinking_config"] = types.ThinkingConfig(thinking_budget=0)
        except Exception:
            pass
    try:
        return client.models.generate_content(
            model=model,
            contents=prompt,
            config=types.GenerateContentConfig(**config_kwargs),
        )
    except Exception as exc:
        if "thinking_config" in config_kwargs and "INVALID_ARGUMENT" in str(exc):
            config_kwargs.pop("thinking_config", None)
            return client.models.generate_content(
                model=model,
                contents=prompt,
                config=types.GenerateContentConfig(**config_kwargs),
            )
        raise


def _extract_with_gemini(alert_text: str, part_code: str) -> dict[str, Any] | None:
    if os.getenv("GEMINI_ENABLED", "false").strip().lower() != "true":
        return None
    api_key = os.getenv("GEMINI_API_KEY", "").strip()
    if not api_key:
        return None
    model = os.getenv("GEMINI_MODEL", "gemini-3.8-flash")
    try:
        from google import genai
        from google.genai import types

        client = genai.Client(
            api_key=api_key,
            http_options=types.HttpOptions(timeout=max(10000, int(os.getenv("GEMINI_TIMEOUT_MS", "10000")))),
        )
        prompt = (
            "Extract structured disruption parameters from the supply chain alert. "
            "Return ONLY a JSON object with keys: affectedHsCode (string, 4-8 digits), "
            "originCountry (2-letter ISO code), tariffDeltaPercent (number), delayDays (integer), "
            "signalType (string, e.g. TARIFF_SHOCK or LOGISTICS_DISRUPTION), confidence (0..1 float). "
            f"PartCode={part_code}\nAlert={alert_text}"
        )
        response = _generate_gemini_content(client, types, model, prompt)
        raw = (response.text or "").strip()
        if raw.startswith("```"):
            raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", raw, flags=re.DOTALL).strip()
        parsed = json.loads(raw)
        if not isinstance(parsed, dict):
            return None
        tariff_delta = parsed.get("tariffDeltaPercent")
        return {
            "affectedHsCode": str(parsed.get("affectedHsCode") or "UNKNOWN"),
            "originCountry": str(parsed.get("originCountry") or "UNKNOWN").upper(),
            "tariffDeltaPercent": float(tariff_delta) if tariff_delta is not None else 0.0,
            "delayDays": int(parsed.get("delayDays") or 0),
            "signalType": str(parsed.get("signalType") or "TARIFF_SHOCK"),
            "confidence": float(parsed.get("confidence") or 0.8),
            "extractionEngine": f"gemini:{model}",
        }
    except Exception as exc:
        logger.warning("Gemini extraction failed (%s): %s", type(exc).__name__, exc)
        return None


def _synthesize_brief_with_gemini(payload: dict[str, Any]) -> str | None:
    if os.getenv("GEMINI_ENABLED", "false").strip().lower() != "true":
        return None
    api_key = os.getenv("GEMINI_API_KEY", "").strip()
    if not api_key:
        return None
    model = os.getenv("GEMINI_MODEL", "gemini-3.8-flash")
    try:
        from google import genai
        from google.genai import types

        client = genai.Client(
            api_key=api_key,
            http_options=types.HttpOptions(timeout=max(10000, int(os.getenv("GEMINI_TIMEOUT_MS", "10000")))),
        )
        prompt = (
            "Write a concise 3-sentence executive brief using ONLY the supplied deterministic JSON. "
            "Do not invent any numbers or IDs. Cite exact evidenceIds, exposureInr, daysOfCover, and state "
            "that human approval is required in dt-backend before execution.\nJSON="
            + json.dumps(payload, default=str)
        )
        response = _generate_gemini_content(client, types, model, prompt)
        text = (response.text or "").strip()
        return text or None
    except Exception as exc:
        logger.warning("Gemini brief synthesis failed (%s): %s", type(exc).__name__, exc)
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
        extracted = _extract_deterministic(req.alertText)
        if extracted["signalType"] != "UNPARSED":
            extracted = _extract_with_gemini(req.alertText, req.partCode) or extracted
        return {"extracted": extracted}

    def _fetch_deterministic_math(self, state: DeepTierBrainState) -> dict[str, Any]:
        req = state["request"]
        extracted = state["extracted"]
        if extracted["signalType"] == "UNPARSED":
            return {"backend_status": "skipped", "warnings": ["Alert needs human review before simulation."]}

        client: DeepTierClient = self.client_factory()
        warnings: list[str] = []
        evidence_ids: list[str] = []
        tariff_delta = extracted["tariffDeltaPercent"]

        annual_spend = req.annualSpendInr if req.annualSpendInr is not None else 4_800_000.0
        cost_fraction = req.costFraction if req.costFraction is not None else 0.60
        units_on_hand = req.unitsOnHand if req.unitsOnHand is not None else 300
        daily_demand = req.dailyDemand if req.dailyDemand is not None else 20
        base_lead = req.leadTimeDays if req.leadTimeDays is not None else 30
        effective_lead = base_lead + extracted["delayDays"]
        late_cost = req.lateCostPerDayInr if req.lateCostPerDayInr is not None else 80_000.0

        # Bound the Java-tool phase below the gateway's 8-second wait. Each
        # running HTTP call also has its own timeout.
        deadline = monotonic() + JAVA_TOOL_BUDGET_SECONDS
        pool = ThreadPoolExecutor(max_workers=4, thread_name_prefix="deeptier-tools")
        responses = {}

        def collect(name, future):
            try:
                result = future.result(timeout=max(0, deadline - monotonic()))
            except DeepTierClientError as exc:
                warnings.append(f"{name} unavailable: {exc}")
            except TimeoutError:
                warnings.append(f"{name} unavailable: Java tool budget exceeded")
            except Exception as exc:
                warnings.append(f"{name} unavailable: {type(exc).__name__}")
            else:
                responses[name] = result
                return result
            return None

        try:
            tier_task = pool.submit(client.get_tier_map)
            cover_task = pool.submit(client.compute_cover, units_on_hand, daily_demand, effective_lead)
            tier_map = collect("get_tier_map", tier_task)
            if tier_map is not None:
                node_countries: dict[str, str] = {}
                for node in tier_map.get("nodes") or []:
                    if isinstance(node, dict) and node.get("nodeId"):
                        nid = str(node["nodeId"])
                        evidence_ids.append(nid)
                        if node.get("country"):
                            node_countries[nid] = str(node["country"]).upper()

                origin_country = str(extracted.get("originCountry") or "UNKNOWN").upper()
                matched_links: list[dict[str, Any]] = []
                for link in tier_map.get("links") or []:
                    if not isinstance(link, dict) or link.get("hsCode") != extracted["affectedHsCode"]:
                        continue
                    from_id = link.get("from") or link.get("fromNodeId")
                    to_id = link.get("to") or link.get("toNodeId")
                    if origin_country != "UNKNOWN":
                        from_country = node_countries.get(str(from_id), "")
                        to_country = node_countries.get(str(to_id), "")
                        if (from_country or to_country) and origin_country not in (from_country, to_country):
                            continue
                    matched_links.append(link)
                    for node_id in (from_id, to_id):
                        if node_id and str(node_id) not in evidence_ids:
                            evidence_ids.append(str(node_id))

                if matched_links and req.annualSpendInr is None and req.costFraction is None:
                    if len(matched_links) == 1:
                        only = matched_links[0]
                        if only.get("annualSpendInr") is not None:
                            annual_spend = float(only["annualSpendInr"])
                        if only.get("costFraction") is not None:
                            cost_fraction = float(only["costFraction"])
                    else:
                        total_affected = sum(
                            float(l.get("annualSpendInr") or 0.0) * float(l.get("costFraction") if l.get("costFraction") is not None else 1.0)
                            for l in matched_links
                        )
                        if total_affected > 0:
                            annual_spend = total_affected
                            cost_fraction = 1.0
                if extracted["affectedHsCode"] != "UNKNOWN":
                    evidence_ids.append(f"HS-{extracted['affectedHsCode']}")

            exposure = {"dutyExposureInr": 0.0, "reason": "No tariff change to evaluate"} if tariff_delta == 0 else None
            exposure_task = None
            if tariff_delta != 0 and monotonic() < deadline:
                exposure_task = pool.submit(
                    client.compute_exposure, annual_spend_inr=annual_spend, cost_fraction=cost_fraction,
                    delta_percentage_points=tariff_delta, evidence_ids=list(evidence_ids),
                )
            whatif_task = None
            if monotonic() < deadline:
                whatif_task = pool.submit(client.simulate_whatif, {
                    "scenarioId": state["scenario_id"],
                    "hsCode": extracted["affectedHsCode"],
                    "originCountry": extracted["originCountry"],
                    "deltaPercentagePoints": tariff_delta,
                    "annualSpendInr": annual_spend,
                    "costFraction": cost_fraction,
                    "unitsOnHand": units_on_hand,
                    "dailyDemand": daily_demand,
                    "leadTimeDays": effective_lead,
                    "lateCostPerDayInr": late_cost,
                    "seed": 20261121,
                })
            if exposure_task is not None:
                exposure = collect("compute_exposure", exposure_task)
                for eid in (exposure or {}).get("evidenceIds") or []:
                    if str(eid) not in evidence_ids:
                        evidence_ids.append(str(eid))
            elif tariff_delta != 0:
                warnings.append("compute_exposure unavailable: Java tool budget exceeded")
            cover = collect("compute_cover", cover_task)
            whatif = collect("simulate_whatif", whatif_task) if whatif_task else None
            if whatif_task is None:
                warnings.append("simulate_whatif unavailable: Java tool budget exceeded")
        finally:
            pool.shutdown(wait=False, cancel_futures=True)

        expected_calls = 3 if tariff_delta == 0 else 4
        backend_status = ("connected" if len(responses) == expected_calls
                          else "partial" if responses else "unreachable")

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

        exposure_inr = exposure.get("dutyExposureInr", exposure.get("exposureInr"))
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

        executive_brief = (
            f"Human review is required for part {req.partCode}. The alert did not identify an HS or HSN code, "
            "a tariff change, or a logistics disruption. No Java simulation was requested."
            if extracted["signalType"] == "UNPARSED" else _synthesize_brief_with_gemini(brief_payload)
        )
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
                    f"{f'INR {exposure_inr}' if exposure_inr is not None else 'unavailable'} and "
                    f"{f'{days_of_cover} days of cover' if days_of_cover is not None else 'unavailable cover'} "
                    f"(gap {gap_days if gap_days is not None else 'unknown'} days). "
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
