package com.bpmforbusiness.laya.worker;

import com.bpmforbusiness.laya.decision.DecisionRegistry;
import com.bpmforbusiness.laya.decision.LayaClient;
import com.bpmforbusiness.laya.decision.LayaDecisionWorker;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Entry point: builds the Zeebe/Camunda client, registers the Laya decision worker,
 * and blocks forever. Configuration via environment variables:
 *
 * <ul>
 *   <li>{@code CAMUNDA_GRPC_ADDRESS} — Zeebe gateway (host:port), default localhost:26500</li>
 *   <li>{@code CAMUNDA_REST_ADDRESS}  — Camunda REST, default http://localhost:8080</li>
 *   <li>{@code CAMUNDA_AUTH_STRATEGY} — "NONE" (dev) or "OAUTH" (SaaS), default OAUTH</li>
 *   <li>{@code CLIENT_ID}/{@code CLIENT_SECRET} — Camunda SaaS/OIDC credentials when OAUTH</li>
 *   <li>{@code LAYA_BASE_URL}          — Laya serve base, default http://localhost:8000</li>
 *   <li>{@code LAYA_API_KEY}           — optional bearer key for the Laya server</li>
 *   <li>{@code LAYA_WORKERS}           — max active jobs, default 4</li>
 * </ul>
 */
public final class LayaDecisionWorkerMain {

    private static final Logger log = LoggerFactory.getLogger(LayaDecisionWorkerMain.class);

    private LayaDecisionWorkerMain() {}

    public static void main(String[] args) throws Exception {
        String grpc = env("CAMUNDA_GRPC_ADDRESS", "localhost:26500");
        String rest = env("CAMUNDA_REST_ADDRESS", "http://localhost:8080");
        String auth = env("CAMUNDA_AUTH_STRATEGY", "OAUTH");
        String layaUrl = env("LAYA_BASE_URL", "http://localhost:8000");
        String layaKey = env("LAYA_API_KEY", "");
        int workers = Integer.parseInt(env("LAYA_WORKERS", "4"));

        DecisionRegistry registry = new DecisionRegistry()
                .register(DecisionRegistry.triagePack());

        ZeebeClientBuilder builder = ZeebeClient.newClientBuilder()
                .gatewayAddress(grpc)
                .defaultJobWorkerMaxJobsActive(workers);
        if ("NONE".equalsIgnoreCase(auth)) {
            builder.usePlaintext();
        } else {
            // Camunda 8 SaaS / OIDC — supply credentials via CLIENT_ID/SECRET + OAUTH_URL.
            String clientId = env("CLIENT_ID", "");
            String secret = env("CLIENT_SECRET", "");
            String oauthUrl = env("OAUTH_URL", "https://login.cloud.camunda.io/oauth/token");
            if (!clientId.isBlank()) {
                builder.credentialsProvider(io.camunda.zeebe.client.CredentialsProvider
                        .newCredentialsProviderBuilder()
                        .clientId(clientId).clientSecret(secret)
                        .authorizationServerUrl(oauthUrl).build());
            }
            builder.restAddress(java.net.URI.create(rest));
        }
        ZeebeClient client = builder.build();

        LayaClient laya = new LayaClient(layaUrl, layaKey, Duration.ofSeconds(30));

        var worker = LayaDecisionWorker.open(client, registry, laya, workers);
        log.info("Laya decision worker ready. Task type='{}', packs={}, laya={}, grpc={}",
                LayaDecisionWorker.taskType(), registry.size(), layaUrl, grpc);

        // Keep the worker alive; open() already started it.
        Thread.currentThread().join();
    }

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return (v == null || v.isBlank()) ? def : v;
    }
}