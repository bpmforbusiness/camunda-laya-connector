package io.bpmforbusiness.laya.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.runtime.core.outbound.operation.ConnectorOperations;
import io.camunda.connector.runtime.core.outbound.operation.OutboundConnectorOperationFunction;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit test of the Laya outbound connector using the SDK's annotations-dispatch
 * harness (same pattern as the official connector-template-outbound). A lightweight
 * in-process HTTP server stands in for the Laya /v1/systemone endpoint.
 */
class LayaDecisionConnectorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static HttpServer fakeLaya() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/v1/systemone",
        exchange -> {
          byte[] body =
              ("{\"model\":\"laya\",\"answers\":{\"department\":{\"type\":\"choice\","
                      + "\"choice\":\"support\"}},\"usage\":{\"tokens\":1}}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    return server;
  }

  private static Object execute(Map<String, Object> variables) {
    var connector = new LayaDecisionConnector();
    var operations =
        ConnectorOperations.from(connector, MAPPER, new DefaultValidationProvider());
    var function = new OutboundConnectorOperationFunction(operations);
    var context =
        OutboundConnectorContextBuilder.create()
            .variables(variables)
            .header("operation", "runDecision")
            .build();
    return function.execute(context);
  }

  @Test
  void runDecision_returnsRegisteredPackAnswers() throws Exception {
    HttpServer server = fakeLaya();
    try {
      Object result =
          execute(
              Map.of(
                  "baseUrl", "http://localhost:" + server.getAddress().getPort(),
                  "pack", "support/triage",
                  "state", Map.of("ticketText", "user says cancel")));

      assertThat(result).isInstanceOf(Map.class);
      Map<?, ?> out = (Map<?, ?>) result;
      assertThat(out.get("pack")).isEqualTo("support/triage");
      assertThat(out.get("model")).isEqualTo("laya");
      com.fasterxml.jackson.databind.JsonNode answers =
          (com.fasterxml.jackson.databind.JsonNode) out.get("answers");
      assertThat(answers).isNotNull();
      assertThat(answers.get("department")).isNotNull();
    } finally {
      server.stop(0);
    }
  }

  @Test
  void runDecision_rejectsUnregisteredPack() {
    assertThatThrownBy(() -> execute(Map.of("pack", "nope", "state", "x")))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("No registered DecisionPack 'nope'");
  }
}