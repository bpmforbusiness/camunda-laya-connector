package io.bpmforbusiness.laya.connector.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.constraints.NotNull;

/**
 * Input for the {@code runDecision} operation. The {@code state} is the context
 * the engine decides over — a JSON object, JSON string, or plain text (the SDK
 * binds whatever the process variable holds).
 */
public record LayaRequest(
    @TemplateProperty(
        group = "Connection",
        label = "Laya Base URL",
        description = "Laya server base URL that answers POST /v1/systemone.",
        type = PropertyType.Text)
    String baseUrl,

    @TemplateProperty(
        group = "Connection",
        label = "Laya API key (optional)",
        description = "Optional bearer token for the Laya server. Reference a secret, e.g. camunda.secrets.MY_KEY.",
        type = PropertyType.Text)
    String apiKey,

    @NotNull
    @TemplateProperty(
        group = "Decision",
        label = "Decision pack",
        description = "Name of a DecisionPack registered in the connector (e.g. support/triage). Only pre-approved packs can be selected.",
        type = PropertyType.Text)
    String pack,

    @NotNull
    @TemplateProperty(
        group = "Decision",
        label = "State",
        description = "The state/context the model decides over (JSON object, JSON string, or plain text).",
        type = PropertyType.Text)
    Object state) {}