package com.bpmforbusiness.laya.decision;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;

/**
 * A registered, pre-approved decision pack. This is the safety + versioning boundary:
 * a BPMN process may only select a pack that this worker has explicitly registered —
 * it can never submit arbitrary question sets or model configuration from process
 * variables. Mirrors the DecisionPack model from the TypeSafe / Camunda-Jev ecosystem
 * and is the "least surprise" boundary recommended for System-1 in orchestration.
 *
 * <p>A pack bundles a stable name with the Jev/Laya question schema (choice / score /
 * noul). The worker talks to a self-hosted Laya server over the Jev-compatible
 * {@code POST /v1/systemone} wire protocol.</p>
 */
public final class DecisionPack {

    private final String name;
    private final String stateVariable;
    private final JsonNode questions;
    private final List<String> inputs;
    private final String outputVariable;

    public DecisionPack(String name, String stateVariable,
                        JsonNode questions, List<String> inputs, String outputVariable) {
        this.name = Objects.requireNonNull(name, "name");
        this.stateVariable = Objects.requireNonNull(stateVariable, "stateVariable");
        this.questions = Objects.requireNonNull(questions, "questions");
        this.inputs = List.copyOf(inputs == null ? List.of() : inputs);
        this.outputVariable = outputVariable == null ? "jev" : outputVariable;
    }

    public String name() { return name; }
    public String stateVariable() { return stateVariable; }
    public JsonNode questions() { return questions; }
    public List<String> inputs() { return inputs; }
    public String outputVariable() { return outputVariable; }

    /** An alias that accepts an internal bare-name too (pack registered as "triage" but
     *  called via "support/triage" in the process). */
    public boolean matches(String requested) {
        return name.equals(requested)
                || requested != null && requested.endsWith("/" + name)
                || name.endsWith("/" + requested);
    }

    @Override
    public String toString() {
        return "DecisionPack{" + name + ", state=" + stateVariable + ", out=" + outputVariable + "}";
    }
}