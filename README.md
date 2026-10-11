# camunda-laya-connector

Run decisions on **Laya** — the open-source System-1 (trained, non-autoregressive)
decision engine — from Camunda 8, two ways:

- **`connector/`** — an official **Camunda Connector SDK outbound connector**
  ("Laya Decision (System 1)"), for use in Camunda Modeler / Hub and the Camunda
  connector runtime. The path for publishing on [Camunda Marketplace](https://marketplace.camunda.com).
- **`worker/`** — a standalone **Zeebe job worker** you deploy and operate yourself
  (`io.bpmforbusiness.laya:decision:1`).

Both share one **`core/`** module (`DecisionPack` + `DecisionRegistry` + `LayaClient`).
Java 21, Maven, MIT.

## Why

Replace an LLM-in-the-loop decision with a fast, auditable, typed decision engine.
A decision engine's output is a **scored candidate, not a fact** — branch on it, but
keep the record for audit.

## Features

- **Agentic-but-typed decisions:** choice / score / yes-no (noul) answers from a
  System-1 engine instead of an LLM call.
- **DecisionPack safety boundary:** workflows may only reference **pre-approved**
  decision packs — never arbitrary question sets or credentials from process variables.
- **Works with your existing stack:** Laya is self-hosted and uses the Jev-compatible
  `POST /v1/systemone` wire protocol — no model-key dependency in the connector itself.

## Architecture

```
core/        DecisionPack, DecisionRegistry, LayaClient  (no Zeebe, no SDK)
worker/      LayaDecisionWorker + Main — Zeebe worker (self-hosted deployable)
connector/   LayaDecisionConnector — Camunda Connector SDK outbound connector
```

## Wire contract

Request (POST `{baseUrl}/v1/systemone`):

```json
{
  "state": "<the state/context to decide over>",
  "questions": {
    "<qid>": { "type": "choice|score|yes-no|noul", "instructions": "...", "criteria": {...} }
  }
}
```

Response (mapped by the process via **Result Variable** / **Result Expression**):

```json
{
  "model": "...",
  "answers": { "<qid>": { "type": "choice", "choice": "...", "probabilities": {}, "confidence": ... } },
  "usage": {}
}
```

## Getting started

### Prerequisites

- **Java 21 + Maven 3.8+**
- A **Laya** server answering `POST /v1/systemone` (self-hosted, Apache-2.0) —
  point either consumer at it via `LAYA_BASE_URL` (worker) or the **Laya Base URL**
  input (connector).
- A Camunda 8 cluster — SaaS or self-managed.

### Build all modules

```bash
mvn clean package          # core (5 tests) + worker (jar) + connector (2 tests + generated template)
```

### Use the Connector (Camunda SDK)

1. Install `connector/element-templates/laya-decision.json` in Camunda Modeler
   (Settings → Element Templates) — it is **generated at build time** by the
   `element-template-generator` plugin, so keep it in sync by rebuilding.
2. Add a **Service Task**, apply **"Laya Decision (System 1)"**, and set:
   - **Laya Base URL** — where `/v1/systemone` is served (default `http://localhost:8000`)
   - **Laya API key (optional)** — a secret reference, e.g. `camunda.secrets.MY_KEY`
   - **Decision pack** — pre-approved pack name (default `support/triage`)
   - **State** — the process variable / expression the engine decides over
   - **Result Variable / Result Expression** — how the typed answers are written back
   - **Error Expression** — optional BPMN error mapping
3. Run the connector in a **connector runtime** (Camunda SaaS connectors, or the
   self-managed connector runtime — Docker `camunda/connectors` with your jar, or the
   Spring Boot starter). The `@OutboundConnector(type = io.bpmforbusiness.laya:decision:1)`
   registers it for job pickup.

### Use the Worker (self-hosted)

```bash
export CAMUNDA_AUTH_STRATEGY=NONE            # dev/plaintext, or OIDC creds for SaaS
export LAYA_BASE_URL=http://localhost:8000
java -jar worker/target/camunda-laya-connector-worker-0.1.0.jar
```

Config env vars: `CAMUNDA_GRPC_ADDRESS`, `CAMUNDA_REST_ADDRESS`,
`CAMUNDA_AUTH_STRATEGY`, `CLIENT_ID`/`CLIENT_SECRET`/`OAUTH_URL`,
`LAYA_BASE_URL`, `LAYA_API_KEY`, `LAYA_WORKERS`. The worker's own element template is
at `worker/element-templates/laya-decision.json`.

## Registered packs

Pre-approved packs live in `DecisionRegistry` (extend as needed). Today:
- **`support/triage`** — decides on the state with `department` (choice:
  billing/support/sales), `urgency` (score: low/medium/high), `churn_risk` (yes-no).

## Safety

- **Zero retries on a decision failure** — an unregistered pack (or missing state)
  fails the task → a visible incident. Do not silently retry a scored answer.
- **No exactly-once from the engine** — downstream idempotency is the workflow's job.
- A scored recommendation is a candidate, not a fact — keep the record.

## Error codes (catch as BPMN errors)

`UNREGISTERED_PACK` · `MISSING_STATE` · `LAYA_CALL_FAILED`

## Publishing

- **Worker:** deploy and run it yourself (see "Use the Worker").
- **Connector → Camunda Marketplace:** the connector repo is public with this
  README, source, and `LICENSE.md`. Submit the listing with a title, description,
  the GitHub URL (`https://github.com/bpmforbusiness/camunda-laya-connector`), a
  feature list, a logo/screenshots (hosted at a downloadable URL), and the support
  links below.

## Support & documentation

- **Developer / this connector:** this README ·
  Camunda [Connector SDK docs](https://docs.camunda.io/docs/components/connectors/custom-built-connectors/connector-sdk/)
- **Laya engine:** [github.com/NandhaKishorM/laya](https://github.com/NandhaKishorM/laya)
- **Support (by Milletech Systems Inc.):** contact **sales@milletechinc.com** ·
  **770-619-0095** · [milletechai.com](https://www.milletechai.com)
- **Vendor site:** [milletechinc.com](https://www.milletechinc.com)

## License

MIT — see `LICENSE.md`.