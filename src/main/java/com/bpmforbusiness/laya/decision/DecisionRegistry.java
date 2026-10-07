package com.bpmforbusiness.laya.decision;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registered {@link DecisionPack}s. Processes select a pack by name; only these
 * are allowed. This is the explicit safety boundary: a BPMN process can never
 * submit arbitrary question sets or credentials — it can only pick a pre-approved
 * pack the operator registered here.
 */
public final class DecisionRegistry {

    private final Map<String, DecisionPack> packs = new LinkedHashMap<>();

    public DecisionRegistry register(DecisionPack pack) {
        packs.put(pack.name(), pack);
        return this;
    }

    /** Resolve by exact name, or by a "namespace/name" suffix match. */
    public DecisionPack resolve(String requested) {
        if (requested == null || requested.isBlank()) return null;
        DecisionPack exact = packs.get(requested);
        if (exact != null) return exact;
        for (DecisionPack p : packs.values()) {
            if (p.matches(requested)) return p;
        }
        return null;
    }

    public int size() { return packs.size(); }

    public static DecisionPack triagePack() {
        // Support-triage example: choice (department) + score (urgency) + noul (churn risk).
        String questions = """
                {
                  "department": {
                    "type": "choice",
                    "instructions": "Which department should handle this?",
                    "criteria": { "billing": "Billing and payments", "support": "Product support", "sales": "Sales" }
                  },
                  "urgency": {
                    "type": "score",
                    "instructions": "How urgent is this?",
                    "criteria": ["low", "medium", "high"]
                  },
                  "churn_risk": {
                    "type": "noul",
                    "instructions": "Does the user threaten to cancel or leave?"
                  }
                }
                """;
        return new DecisionPack("support/triage", "ticketText", parse(questions),
                java.util.List.of("ticketText"), "jev");
    }

    private static com.fasterxml.jackson.databind.JsonNode parse(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid questions JSON", e);
        }
    }
}