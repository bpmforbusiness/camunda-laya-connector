# camunda-laya-connector

Camunda 8 service-task worker that runs decisions on **Laya** — the open-source
System-1 (trained, non-autoregressive) decision engine — as BPMN service tasks in
Camunda 8. Java 21, Maven, MIT.

Why: replace an LLM-in-the-loop decision with a fast, auditable, typed decision
engine. A decision engine's output is a **scored candidate, not a fact** — branch
on it, but keep the record for audit.

- **Task type:** `io.bpmforbusiness.laya:decision:1`
- **Wire protocol:** Jev-compatible `POST {LAYABASE_URL}/v1/systemone`
- **Safety boundary:** processes may only reference a **pre-approved DecisionPack**
  registered in the worker — never arbitrary question sets or credentials from
  process variables.

---

## Layout

```
src/main/java/com/bpmforbusiness/laya/
  worker/LayaDecisionWorkerMain.java   Entry point: builds the Zeebe client from env,
                                       registers the worker, blocks forever.
  decision/LayaDecisionWorker.java     JobHandler: resolves pack, validates state,
                                       calls Laya, writes the result; zero-retry fail
                                       (→ incident) on unregistered pack / missing state.
  decision/LayaClient.java             Thin HTTP client for POST /v1/systemone.
  decision/DecisionRegistry.java       Pre-approved packs (only these can be selected).
  decision/DecisionPack.java           A pack: stable name + question schema + in/out vars.
element-templates/laya-decision.json   Camunda Modeler element template ("Laya Decision (System 1)").
src/test/.../DecisionPackTest.java     5 unit tests (registry resolution, wire mapping).
```

## Wire contract

Request (the worker POSTs this to `{LAYABASE_URL}/v1/systemone`):

```json
{
  "state": "<the process state/context to decide over>",
  "questions": {
    "<qid>": { "type": "choice|score|yes-no|noul", "instructions": "...", "criteria": {...} }
  }
}
```

Response (written back to the process under the result variable, default `jev`):

```json
{
  "model": "...",
  "answers": {
    "<qid>": { "type": "choice", "choice": "...", "probabilities": {...}, "confidence": ... }
  },
  "usage": {...}
}
```

The handler writes `{ "pack", "model", "answers", "usage" }` to the output variable so
the BPMN process can branch on individual question results.

## Configuration (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `CAMUNDA_GRPC_ADDRESS` | `localhost:26500` | Zeebe gateway host:port |
| `CAMUNDA_REST_ADDRESS` | `http://localhost:8080` | Camunda REST address (SaaS) |
| `CAMUNDA_AUTH_STRATEGY` | `OAUTH` | `NONE` = dev/plaintext, `OAUTH` = Camunda SaaS/OIDC |
| `CLIENT_ID` / `CLIENT_SECRET` | *(empty)* | Camunda SaaS client credentials (when OAUTH) |
| `OAUTH_URL` | `https://login.cloud.camunda.io/oauth/token` | OIDC token endpoint |
| `LAYABASE_URL` | `http://localhost:8000` | Laya serve base URL |
| `LAYA_API_KEY` | *(empty)* | Optional bearer key for the Laya server |
| `LAYA_WORKERS` | `4` | Max active jobs per worker |

## Build & test

Requires **Java 21** and **Maven 3.8+**.

```bash
mvn clean package        # runs the 5 unit tests, then shades a runnable fat jar
# → target/camunda-laya-connector-0.1.0.jar
```

The shaded jar's manifest main class is `com.bpmforbusiness.laya.worker.LayaDecisionWorkerMain`
(custom workers subclass/`open()` on `LayaDecisionWorker`; the deployable entry point is the `...Main` class).

## Run — local dev (plaintext, no cluster)

1. Start Laya so it answers `POST /v1/systemone` on `http://localhost:8000`
   (e.g. `laya[serve]`, or its container image; Laya is Apache-2.0, self-hosted).
2. Accept a plaintext connection from a local Camunda (or skip — see SaaS below):

```bash
export CAMUNDA_AUTH_STRATEGY=NONE            # plaintext gRPC, no TLS
export LAYABASE_URL=http://localhost:8000
java -jar target/camunda-laya-connector-0.1.0.jar
```

## Run — against Camunda 8 SaaS

1. In the Camunda **Console**: create a **Client** (API → Manage → Create
   client) and grant it cluster permissions; note the `ClusterId`, region +
   the client `ClientId` and `ClientSecret`.
2. Point the worker at the cluster and the Laya server:

```bash
export CLIENT_ID=...            # Camunda SaaS client id
export CLIENT_SECRET=...        # Camunda SaaS client secret
export CAMUNDA_GRPC_ADDRESS=<clusterId>.<region>.zeebe.camunda.io:443
export CAMUNDA_REST_ADDRESS=https://<clusterId>.<region>.zeebe.camunda.io:443
export LAYABASE_URL=http://<laya-host>:8000
java -jar target/camunda-laya-connector-0.1.0.jar
```

Log line to confirm it's up:
`Laya decision worker ready. Task type='io.bpmforbusiness.laya:decision:1', ...`

## Use in Camunda Modeler

1. **Install the element template:** Menu → *Settings* → *Element Templates* →
   add `element-templates/laya-decision.json` (or copy it into
   `~/.camunda/element-templates/`).
2. Add a **Service Task** and apply the **"Laya Decision (System 1)"** template.
3. Set:
   - **Decision pack** → a pack registered in the worker (default `support/triage`)
   - **State variable** → the process variable holding the state to decide over
     (default `ticketText`)
   - **Result variable name** → where the decision record is written (default `jev`)
4. Deploy the process, start an instance, and the worker completes the task,
   writing `{ pack, model, answers, usage }` to the result variable.

## Registered packs

Only pre-approved packs can be selected (the safety/versioning boundary). Today:

- **`support/triage`** — decides on the `ticketText` variable with three questions:
  - `department` (choice: billing / support / sales)
  - `urgency` (score: low / medium / high)
  - `churn_risk` (yes-no / noul)

Add more packs in `DecisionRegistry` (and keep them versioned by name).

## Operational safety

- **Zero retries on a decision failure.** An unregistered pack or a missing state
  variable fails the job with `retries=0` → a visible incident. Do not silently
  retry a scored answer.
- **No exactly-once from the engine** — downstream idempotency is the workflow's
  job, not the engine's.
- A scored recommendation is a candidate, not a fact — retain the record for audit.

## Publishing to Camunda (becoming an official connector)

This repo ships a self-contained **Zeebe job worker** — the right artifact when you
deploy and operate it yourself. To have it listed in Camunda's connector catalog /
Modeler so others can install it, convert it to a **Camunda Connector SDK outbound
connector** and contribute to [`camunda/connectors`](https://github.com/camunda/connectors)
(or publish your own connector repo following those conventions). The step-by-step
for both routes is in the project's publish checklist (ask the maintainer / see the
README source history). Key deltas for the SDK route:
`io.camunda.connector` SDK + `@OutboundConnector(name, type, inputVariables)` +
generated element template + secret-aware properties + connector test harness +
Camunda's contribution criteria (Apache-2.0, marketplace descriptor).

## License

MIT. A decision engine output is a scored candidate, not a fact.