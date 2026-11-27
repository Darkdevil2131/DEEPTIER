package com.deeptier.sap;

import com.deeptier.api.dto.RerouteResponse;
import com.deeptier.config.DeepTierProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonAgentGatewayTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PythonAgentGateway gateway = new PythonAgentGateway(
            new DeepTierProperties(null, new DeepTierProperties.InternalProps("test-key"), null, null),
            mapper,
            "http://127.0.0.1:8000",
            ""
    );

    @Test
    void maps500kgKochiChennaiRouteAndGatesCorrectly() throws Exception {
        String json = """
                {
                  "shipment_id": "SHIP-500",
                  "status": "completed",
                  "data_provenance": ["ESTIMATED", "USER_PROVIDED"],
                  "agents": {
                    "route_optimization": {
                      "data": {
                        "no_feasible_route": false,
                        "routes": [
                          {
                            "mode": "road",
                            "distance_km": 782.1,
                            "transit_hours_estimate": 16.3,
                            "cost_estimate_inr": 9776.5,
                            "feasible": true,
                            "decision_score": 0.1675,
                            "legs": [
                              {
                                "id": "IN-ROAD-KOCHI-THRISSUR",
                                "from": "kochi",
                                "to": "thrissur",
                                "mode": "road",
                                "hours": 1.7,
                                "distance_km": 81.6
                              }
                            ]
                          }
                        ],
                        "warnings": []
                      }
                    },
                    "logistics_orchestrator": {
                      "data": {
                        "decision": {
                          "status": "ready_for_human_approval",
                          "commercial_evidence": "user_provided",
                          "candidate": {
                            "option": "road",
                            "mode": "road",
                            "freight_inr": 9776.5,
                            "base_transit_hours": 16.3,
                            "disruption_ids": []
                          },
                          "gates": [
                            {"gate": "route_feasibility", "status": "passed", "reason": "Route capacity OK"},
                            {"gate": "documents", "status": "passed", "reason": "All required docs present"}
                          ]
                        }
                      }
                    }
                  }
                }
                """;
        JsonNode root = mapper.readTree(json);
        RerouteResponse res = gateway.mapRerouteResponse(root);

        assertTrue(res.feasible());
        assertEquals("ready_for_human_approval", res.decisionStatus());
        assertNotNull(res.selectedRoute());
        assertEquals("road", res.selectedRoute().option());
        assertEquals(0, new BigDecimal("9776.5").compareTo(res.costEstimateInr()));
        assertEquals(16.3, res.transitHoursEstimate());
        assertEquals(1, res.legs().size());
        assertEquals("IN-ROAD-KOCHI-THRISSUR", res.legs().getFirst().legId());
        assertEquals("kochi", res.legs().getFirst().fromHub());
        assertEquals("thrissur", res.legs().getFirst().toHub());
        assertEquals(2, res.decision().gateReasons().size());
        assertEquals(0.1675, res.alternatives().getFirst().score());
    }

    @Test
    void maps500000kgOverCapacityAsInfeasibleAndBlocked() throws Exception {
        String json = """
                {
                  "shipment_id": "SHIP-500K",
                  "status": "completed",
                  "data_provenance": ["ESTIMATED"],
                  "agents": {
                    "route_optimization": {
                      "data": {
                        "no_feasible_route": true,
                        "routes": [
                          {
                            "mode": "road",
                            "feasible": false,
                            "cost_estimate_inr": 999999.0,
                            "transit_hours_estimate": 20.0
                          }
                        ],
                        "warnings": []
                      }
                    },
                    "logistics_orchestrator": {
                      "data": {
                        "decision": {
                          "status": "blocked",
                          "commercial_evidence": "unavailable",
                          "candidate": null,
                          "gates": [
                            {"gate": "route_feasibility", "status": "blocked", "reason": "No route satisfies configured constraints."}
                          ]
                        }
                      }
                    }
                  }
                }
                """;
        RerouteResponse res = gateway.mapRerouteResponse(mapper.readTree(json));

        assertFalse(res.feasible());
        assertEquals("blocked", res.decisionStatus());
        assertNull(res.selectedRoute());
        assertNull(res.decision().recommendedOption());
        assertNull(res.costEstimateInr());
        assertTrue(res.decision().gateReasons().getFirst().contains("route_feasibility: blocked"));
    }

    @Test
    void distinguishesFeasibleRouteFromBlockedCommercialGate() throws Exception {
        String json = """
                {
                  "shipment_id": "SHIP-NO-DOCS",
                  "status": "completed",
                  "data_provenance": ["ESTIMATED"],
                  "agents": {
                    "route_optimization": {
                      "data": {
                        "no_feasible_route": false,
                        "routes": [
                          {
                            "mode": "road",
                            "feasible": true,
                            "cost_estimate_inr": 9776.5,
                            "transit_hours_estimate": 16.3
                          }
                        ]
                      }
                    },
                    "logistics_orchestrator": {
                      "data": {
                        "decision": {
                          "status": "blocked",
                          "commercial_evidence": "unavailable",
                          "candidate": null,
                          "gates": [
                            {"gate": "documents", "status": "blocked", "reason": "Required documents missing."}
                          ]
                        }
                      }
                    }
                  }
                }
                """;
        RerouteResponse res = gateway.mapRerouteResponse(mapper.readTree(json));

        assertTrue(res.feasible());
        assertEquals("blocked", res.decisionStatus());
        assertNotNull(res.selectedRoute());
        assertEquals(0, new BigDecimal("9776.5").compareTo(res.costEstimateInr()));
    }
}
