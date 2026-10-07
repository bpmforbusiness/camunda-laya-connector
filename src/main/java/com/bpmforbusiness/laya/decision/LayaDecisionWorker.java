package com.bpmforbusiness.laya.decision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Camunda 8 job worker that runs a Laya (System-1) decision and writes the decision
 * record back to the process. Task type {@code io.bpmforbusiness.laya:decision:1}.
 *
 * <p>Inputs (from process variables):
 * <ul>
 *   <li>{@code jevPack}  — name of a registered {@link DecisionPack}</li>
 *   <li>{@code jevState} — the state/context to decide over (string or object)</li>
 * </ul>
 *
 * <p>Output: the worker writes {@code jev} with the full Laya response ({@code answers}),
 * so the process can branch on individual question results. Unknown / unregistered packs
 * are a hard failure with zero retries, raising an incident (surface, don't hide).</p>
 */
public final class LayaDecisionWorker implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(LayaDecisionWorker.class);
    private static final String TASK_TYPE = "io.bpmforbusiness.laya:decision:1";

    private final DecisionRegistry registry;
    private final LayaClient laya;
    private final ObjectMapper mapper = new ObjectMapper();

    public LayaDecisionWorker(DecisionRegistry registry, LayaClient laya) {
        this.registry = registry;
        this.laya = laya;
    }

    @Override
    public void handle(JobClient client, ActivatedJob job) throws Exception {
        Map<String, Object> vars = job.getVariablesAsMap();
        String packName = (String) vars.getOrDefault("jevPack", "");
        DecisionPack pack = registry.resolve(packName);
        if (pack == null) {
            log.error("[{}] unregistered decision pack '{}'; failing job {} with incident",
                    job.getProcessInstanceKey(), packName, job.getKey());
            client.newFailCommand(job.getKey()).retries(0)
                    .errorMessage("Unregistered DecisionPack '" + packName
                            + "'. Register it in the worker, or fix the process variable.")
                    .send().join();
            return;
        }

        // Task header may override the output variable (element-template sets "outVariable").
        String outVariable = job.getCustomHeaders().getOrDefault("outVariable", pack.outputVariable());

        Object state = vars.get(pack.stateVariable());
        if (state == null) {
            client.newFailCommand(job.getKey()).retries(0)
                    .errorMessage("Missing state variable '" + pack.stateVariable()
                            + "' for pack '" + pack.name() + "'.")
                    .send().join();
            return;
        }

        JsonNode answers = laya.systemOne(mapper.valueToTree(state), pack.questions());

        Map<String, Object> result = new HashMap<>();
        result.put("pack", pack.name());
        result.put("model", answers.path("model").asText(null));
        result.put("answers", answers.path("answers"));
        result.put("usage", answers.path("usage"));
        Map<String, Object> writeVars = new HashMap<>();
        writeVars.put(outVariable, result);

        client.newCompleteCommand(job.getKey()).variables(writeVars).send().join();
        log.info("[{}] pack={} decision written to '{}'", job.getProcessInstanceKey(), pack.name(),
                outVariable);
    }

    public static String taskType() { return TASK_TYPE; }

    /** Convenience: open the worker on a client with sensible defaults. */
    public static io.camunda.zeebe.client.api.worker.JobWorker open(ZeebeClient client,
                                                                    DecisionRegistry registry,
                                                                    LayaClient laya,
                                                                    int workersCount) {
        return client.newWorker()
                .jobType(TASK_TYPE)
                .handler(new LayaDecisionWorker(registry, laya))
                .name("laya-" + TASK_TYPE.replaceAll("[^a-zA-Z0-9]", "-"))
                .maxJobsActive(workersCount)
                .timeout(java.time.Duration.ofSeconds(30))
                .open();
    }
}