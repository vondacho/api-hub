# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An API contract lifecycle platform: it parses, validates, scores, and applies overlays to API contracts (OpenAPI, AsyncAPI, GraphQL SDL, WSDL), registers them, and surfaces them through a catalogue. See `README.md` for the full feature list and the target microservice topology; `doc/arch/` holds the C4 component diagram (`.puml` source + rendered `.png`).

The repo is a **monorepo of standalone modules** — there is no root aggregator pom. Each module builds and deploys independently, so run build/test commands from inside the module directory.

## Modules (present today)

| Path | Stack | Role |
|------|-------|------|
| `api-onboarding/` | Java 25 / Spring Boot 3.5 (Maven) | **Primary module.** REST microservice for parse/validate/score/overlay + registration. Hexagonal. |
| `api-registry/` | Strapi 5.49 (Node 20–24) | Headless CMS persisting registered specs, plus its admin web UI. Treated as an external system, reached only over its REST API. |
| `api-registry-db/` | PostgreSQL 17 (Dockerfile only) | The database `api-registry` persists to. No application code — an image plus its `initdb/` bootstrap. |
| `api-portal/` | Astro 6 (Node ≥22.12) | The **Catalogue** web frontend: list, search, view specs. |
| `api-scorer/` | Fastify 5 / TypeScript (Node ≥22.12) | The **Scoring** microservice. Hexagonal, DI via `@fastify/awilix`. Grades specs with Spectral embedded as a library behind an outbound port. |

**The registry runs on PostgreSQL, not MongoDB.** Strapi 5 reaches its database
through Knex and ships connectors for `postgres`, `mysql` and `sqlite` only —
see the `connections` map in `api-registry/config/database.ts`. Mongoose support
was dropped after Strapi v3, so a document store is not an option here. The
stock in-file SQLite default is used only by `npm run develop`; in a cluster the
registry talks to `api-registry-db`.

**The Scoring engine lives in `api-scorer/`, as a library — not as a separate service.** `api-onboarding` reaches `api-scorer` over REST via `adapter/out/spectral`; `api-scorer` in turn runs Spectral in-process behind its own `ScorerDelegate` outbound port. Jentic is still unimplemented.

Two things about `api-scorer` are easy to get wrong:

- **Scoring is split between the ruleset and the domain, deliberately.** Which rules run, how serious each is, and which dimension it belongs to are *ruleset* concerns and live in `api-scorer/rulesets/evaluation.yaml`. What a broken rule is worth and the score→grade bands are *policy* and live in `src/domain/scoring.ts`. Retuning a deployment must stay a config change; if a task pushes rule-specific knowledge into the domain, that is the wrong layer.
- **Only Spectral-backed dimensions are emitted** — FC, SEC, DX, MR. `ARAX`, `AU` and `AUD` are in the contract but nothing measures them, so they are never returned. This also keeps a latent naming mismatch dormant: the contract says `AUD` while onboarding's `Scorecard.Dimension` says `AID`, and `SpectralRestAdapter` would throw on `valueOf("AUD")`. Do not "fix" one side in isolation.

## Build & test — api-onboarding (the main work happens here)

No Maven wrapper is committed; use a system `mvn`. Java 25 toolchain required.

```bash
cd api-onboarding
mvn clean verify           # full build: codegen → compile → bundle test specs → test
mvn test                   # run all tests (surefire; there is no failsafe/IT phase)
mvn test -Dtest=RegistrationServiceTest        # single test class
mvn test -Dtest=SpectralRestAdapterTest#<method>  # single method
```

### The build has two non-obvious generation phases

1. **`generate-sources` — contract-first client generation.** `openapi-generator-maven-plugin` generates the Strapi client (`strapi-client`) and Scorer client (`scorer-client`) from the specs under `src/main/resources/api/{strapi,scoring}/`. A post-processing script (`scripts/fix-openapi-generator-issue.sh`) then patches the generated Strapi interfaces to work around a map-typed query-param bug. **Generated code lives in `target/generated-sources/` — never hand-edit it; change the spec and regenerate.**

2. **`process-test-resources` — spec bundling for tests.** The `bundle_*.sh` scripts (`scripts/`) run a `redocly bundle` → `zuplo openapi overlay` → optional `redocly lint` pipeline to produce the resolved contracts the tests load from the classpath. **These scripts shell out to `npx`, so a Node toolchain must be on PATH for the test phase to succeed.**

## api-onboarding architecture — Hexagonal (Ports & Adapters)

Package root `io.obya.api.onboarding`. Dependencies point inward; the domain knows nothing of Spring, Strapi, or Spectral.

- **`domain/`** — pure model, no framework imports.
- **`appl/`** — application layer. `usecase/` holds the use-case services (e.g. registration) and `processing/` the per-format parse/validate logic (`oas`, `oai`, `aas`, `graphql`, `wsdl`, `reader`). `appl/out/` defines the **outbound ports** (interfaces the domain calls).
- **`adapter/in/web/`** — inbound REST adapter: controllers, `model` (web DTOs), `exception` handlers, `config`.
- **`adapter/out/`** — outbound adapters implementing the ports: `strapi/` (registry persistence), `spectral/` + `jentic/` (scoring). All external-system concerns — auth, pagination, response shape, **and resilience (Resilience4j retry/circuit breaker/timeout)** — live here, never in `appl` or `domain`.
- `com.ibm.oas.overlay` — vendored OpenAPI Overlay utility; `io.obya.common.util` — shared helpers (e.g. `Try`).

### Testing strategy (all under surefire as `*Test`)

The taxonomy is deliberate — match new tests to the right layer:

- **Functional / acceptance** — Cucumber. `RegistrationServiceTest` is a JUnit-Platform `@Suite` that runs `src/test/resources/feature/registration/*.feature` against the `RegistrationService` with **both Registry and Scorer mocked**. Step defs: `RegistrationCucumberSteps`.
- **Contract conformance** — Microcks (testcontainers) validates the onboarding REST API against its contract (`RegistrationContractTest`).
- **E2E** — RestAssured against the running REST API (`RegistrationE2ETest`, `ResillientRegistrationE2ETest`).
- **Adapter integration** — outbound adapters tested against Microcks (Strapi) / WireMock, with `Resilient*` variants covering the resilience wrapping.
- **Unit** — JUnit 5 + AssertJ on domain/util.
- **`playground/` subpackages** — exploratory/spike tests, not part of the acceptance guarantee.

## Deployment — Helm (`helm/`)

One standalone chart per component, mirroring the monorepo convention (no umbrella chart).
`helm/api-onboarding/`, `helm/api-portal/`, `helm/api-registry/`, `helm/api-registry-db/`
and `helm/api-scorer/` all exist. Onboarding's `values.yaml` still defaults **both** outbound
adapters to `dummy` so every chart is deployable on its own, but its `values-local.yaml` now
sets `scorer.adapter=spectral` and points at the deployed `api-scorer`. `registry.adapter=strapi`
also has a real service to point at, but flipping it needs the `specification` permissions
granted to Strapi's Public role (`adapter/out/strapi` sends no API token). See `helm/README.md`.

```bash
cd api-onboarding  && docker build -t api-onboarding:dev .   # nerdctl --namespace k8s.io build … on containerd
cd api-portal      && docker build -t api-portal:dev .
cd api-registry    && docker build -t api-registry:dev .
cd api-registry-db && docker build -t api-registry-db:dev .
cd api-scorer      && docker build -t api-scorer:dev .

# api-registry-db first: Strapi migrates its schema on boot and crash-loops until the
# database answers.
helm upgrade --install api-registry-db helm/api-registry-db \
  -n api-hub --create-namespace -f helm/api-registry-db/values-local.yaml
helm upgrade --install api-registry helm/api-registry \
  -n api-hub --create-namespace -f helm/api-registry/values-local.yaml
helm upgrade --install api-onboarding helm/api-onboarding \
  -n api-hub --create-namespace -f helm/api-onboarding/values-local.yaml
helm upgrade --install api-portal helm/api-portal \
  -n api-hub --create-namespace -f helm/api-portal/values-local.yaml
helm upgrade --install api-scorer helm/api-scorer \
  -n api-hub --create-namespace -f helm/api-scorer/values-local.yaml
```

**api-onboarding**

- **`-Dbundle.skip=true`** skips the `process-test-resources` spec bundling (the only Node/`npx`
  dependency in the build); the image build uses it so no Node toolchain is needed in the builder
  stage. Contract-first codegen at `generate-sources` still runs.
- Chart config flows through a ConfigMap mounted at `/app/config/application.yaml`, which Spring
  Boot layers **over** the jar's `application.yaml` — only put overrides in `values.yaml`, never
  a duplicate of the whole file. Free-form additions go under `app.extraConfig`.
- Probes use the Actuator health groups (`/actuator/health/{liveness,readiness}`).

**api-portal**

- Deployed as a **server-rendered** Node app (`output: 'server'` + `@astrojs/node` standalone on
  port 4321), so its calls to `api-onboarding` happen over in-cluster DNS instead of from the
  browser. Keep it that way — a static build would force exposing onboarding publicly.
- Chart config flows through a ConfigMap consumed with `envFrom`; every `app.*` key becomes an
  env var read server-side. Probes hit `/healthz` (`src/pages/healthz.ts`).
- `values-local.yaml` enables a Traefik ingress at `http://api-portal.localhost`.

**api-scorer**

- Service port **8081** onto container port 3000, because `api-onboarding`'s chart
  already points its scorer at `http://api-scorer:8081`. Pointing onboarding at it
  also needs `app.spectral.baseUrl=http://api-scorer:8081/api/v1` — the chart's
  default ends in `/api`, but the contract serves `/api/v1`.
- `app.evaluation.content` replaces `rulesets/evaluation.yaml` through a mounted
  ConfigMap, which is how scoring is retuned without a rebuild. It replaces the
  file wholesale — a partial document collapses every rule into `FC`.
- `values-local.yaml` sets `scorer.adapter=spectral`; the chart default is `dummy`
  so it stays deployable standalone.

**api-registry / api-registry-db**

- Install the database first; Strapi migrates its schema on boot and crash-loops until it can
  connect.
- The password lives in **one** place: the Secret the `api-registry-db` release owns. The
  registry chart reads it with a `secretKeyRef` — never copy it into `values.yaml`.
- Generated secrets (the database password, and Strapi's `APP_KEYS` / `ENCRYPTION_KEY` / …) are
  read back from the live Secret on upgrade instead of being regenerated. Rotating
  `ENCRYPTION_KEY` makes stored encrypted fields unreadable, so leave that read-back rule alone.
- The registry image uses **selective copies**, not Strapi's documented `COPY /opt/app ./`, so
  anything read at run time must be listed in the Dockerfile — including `tsconfig.json`, which
  `strapi start` reads to locate `dist/`. Omitting it yields
  `Cannot destructure property 'client' of 'db.config.connection'`.
- `values-local.yaml` enables a Traefik ingress at `http://api-registry.localhost`; the admin UI
  is at `/admin`. `app.url` must match that host or the panel calls back to an address the
  browser cannot resolve. The database is deliberately not exposed.

## api-registry (Strapi) & api-portal (Astro)

```bash
cd api-registry && npm run develop     # Strapi dev (also: build, start, console)
cd api-portal   && npm run dev          # Astro dev (also: build, preview)
```

- **Registry** is a black box behind `api-onboarding`'s `adapter/out/strapi`. Do not import it as a library or reach into its internals from onboarding code — go through the REST adapter and its outbound port.
- `npm run develop` still uses the in-file SQLite default (`DATABASE_CLIENT` unset). Point `DATABASE_*` at a PostgreSQL instance to reproduce the deployed configuration locally; `config/database.ts` already carries the connector.
- **Portal** types are generated from the contract-first OpenAPI spec (`openapi-typescript`). The spec is upstream and read-only from the frontend's view — don't hand-write types that duplicate the contract, and don't edit specs in frontend code.

## Working conventions

- **Contract-first is a hard rule.** If a task tempts you to hand-edit generated clients/types, stop — edit the source spec and regenerate.
- If a change would require importing a framework/library type into `domain` or a port, an adapter-layer abstraction is missing — surface that rather than leaking the dependency.
- Put any retry/circuit-breaker/timeout logic in an adapter, not in a use case or domain service.
- Development is contract-driven and test-driven: for new controller/integration/functional behaviour, write the failing test (Cucumber feature, contract test, or adapter IT) first.
