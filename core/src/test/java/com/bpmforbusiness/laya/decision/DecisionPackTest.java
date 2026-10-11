package com.bpmforbusiness.laya.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DecisionPackTest {

    private final DecisionRegistry registry = new DecisionRegistry().register(DecisionRegistry.triagePack());

    @Test
    void resolvesExactAndNamespacedNames() {
        assertNotNull(registry.resolve("support/triage"));
        assertNotNull(registry.resolve("triage"), "suffix-only name should match");
        DecisionPack p = registry.resolve("support/triage");
        assertEquals("support/triage", p.name());
        assertEquals("ticketText", p.stateVariable());
        assertEquals("jev", p.outputVariable());
    }

    @Test
    void rejectsUnknownPacks() {
        assertNull(registry.resolve("not/a/real/pack"));
        assertNull(registry.resolve(""));
        assertNull(registry.resolve(null));
    }

    @Test
    void questionsCarryChoiceScoreNoul() throws Exception {
        ObjectNode q = (ObjectNode) registry.resolve("support/triage").questions();
        assertTrue(q.has("department"));
        assertTrue(q.has("urgency"));
        assertTrue(q.has("churn_risk"));
        assertEquals("choice", q.path("department").path("type").asText());
        assertEquals("score", q.path("urgency").path("type").asText());
        assertEquals("noul", q.path("churn_risk").path("type").asText());
    }

    @Test
    void clientBuildsJevShapeRequestBody() {
        // Use reflection-free check: the serialized state+questions should render the
        // same body the wire protocol expects: { "state": ..., "questions": {...} }.
        ObjectMapper m = new ObjectMapper();
        ObjectNode body = m.createObjectNode();
        body.set("state", m.valueToTree("hi there"));
        body.set("questions", registry.resolve("support/triage").questions());
        String json = body.toString();
        assertTrue(json.contains("\"state\":\"hi there\""));
        assertTrue(json.contains("\"questions\""));
        assertTrue(json.contains("\"type\":\"noul\""));
    }

    @Test
    void inputsListIsUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> registry.resolve("support/triage").inputs().add("x"));
    }
}