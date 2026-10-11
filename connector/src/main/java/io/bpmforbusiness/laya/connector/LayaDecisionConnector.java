package io.bpmforbusiness.laya.connector;

import com.bpmforbusiness.laya.decision.DecisionPack;
import com.bpmforbusiness.laya.decision.DecisionRegistry;
import com.bpmforbusiness.laya.decision.LayaClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.bpmforbusiness.laya.connector.model.LayaRequest;
import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.annotation.Variable;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Camunda 8 outbound connector that runs a <b>Laya</b> (open-source System-1)
 * typed decision by selecting a pre-approved {@link DecisionPack} and posting to
 * {@code POST /v1/systemone}.
 *
 * <p>Built with the Connector SDK per the Camunda docs (connector-core + automatic
 * element-template generation). Returns the typed answers as an object the user
 * can map with the connector's <i>Result Variable</i> / <i>Result Expression</i>.</p>
 *
 * <p>Error codes users can catch as BPMN errors:
 * <ul>
 *   <li>{@code UNREGISTERED_PACK} — the pack name is not registered in the connector</li>
 *   <li>{@code MISSING_STATE} — the state input is empty</li>
 *   <li>{@code LAYA_CALL_FAILED} — the Laya server returned a non-200 or was unreachable</li>
 * </ul></p>
 */
@OutboundConnector(name = "Laya Decision (System 1)", type = "io.bpmforbusiness.laya:decision:1")
@ElementTemplate(
    id = "com.bpmforbusiness.laya:decision:1",
    name = "Laya Decision (System 1)",
    version = 1,
    description =
        "Run a Laya (open-source System-1) typed decision via POST /v1/systemone. Selects a "
            + "pre-approved DecisionPack by name; returns the typed answers (choice/score/noul) "
            + "for result mapping.",
    icon = "icon.svg",
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/custom-built-connectors/connector-sdk/")
public class LayaDecisionConnector implements OutboundConnectorProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(LayaDecisionConnector.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String DEFAULT_BASE_URL = "http://localhost:8000";
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

  /** Pre-approved packs (safety/versioning boundary). Add more here. */
  private static final DecisionRegistry REGISTRY =
      new DecisionRegistry().register(DecisionRegistry.triagePack());

  @Operation(id = "runDecision", name = "Run a Laya decision")
  public Object runDecision(@Variable LayaRequest request) {
    DecisionPack pack = REGISTRY.resolve(request.pack());
    if (pack == null) {
      throw new ConnectorException(
          "UNREGISTERED_PACK",
          "No registered DecisionPack '" + request.pack()
              + "'. Register it in DecisionRegistry, or fix the 'pack' input.");
    }

    JsonNode state = MAPPER.valueToTree(request.state());
    if (state == null || (state.isTextual() && state.asText().isBlank())) {
      throw new ConnectorException("MISSING_STATE", "The 'state' input must not be empty.");
    }

    String baseUrl =
        (request.baseUrl() == null || request.baseUrl().isBlank())
            ? DEFAULT_BASE_URL
            : request.baseUrl();
    LayaClient client = new LayaClient(baseUrl, request.apiKey(), DEFAULT_TIMEOUT);

    try {
      JsonNode answers = client.systemOne(state, pack.questions());
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("pack", pack.name());
      out.put("model", answers.path("model").asText(null));
      out.put("answers", answers.path("answers"));
      out.put("usage", answers.path("usage"));
      LOGGER.info(
          "Laya decision pack={} completed (answers={})",
          pack.name(), answers.path("answers").size());
      return out;
    } catch (Exception e) {
      throw new ConnectorException("LAYA_CALL_FAILED", truncated(e));
    }
  }

  private static String truncated(Throwable t) {
    String m = t.getMessage();
    if (m == null) return String.valueOf(t);
    return m.length() <= 300 ? m : m.substring(0, 300) + "…";
  }
}