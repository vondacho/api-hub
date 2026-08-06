# api-scorer

The Scoring microservice. It implements `api/scoring/scoring_v1.openapi.yaml` —
accept a candidate API specification, return a scorecard.

Node 22.12+ / Fastify 5 / TypeScript. Standalone module, like every other
component in this monorepo: build and run it from inside this directory.

## Commands

```bash
npm install
npm run generate    # contract-first codegen (runs automatically before dev/build)
npm run dev         # tsx watch, http://localhost:3000
npm run build       # generate + tsc → dist/
npm run start       # node dist/main.js
npm run typecheck   # tsc --noEmit
```

```bash
curl -X POST http://localhost:3000/api/v1/scorings \
  -H 'content-type: application/json' \
  -d '{"source":"https://example.com/petstore.yaml","contract":"OPENAPI"}'
```

## Architecture — Hexagonal (Ports & Adapters)

Same layering as `api-onboarding`, so the two modules read the same way.
Dependencies point inward: `domain/` imports nothing, `appl/` imports only
`domain/`, `adapter/` imports both.

```
src/
├── main.ts                     process entrypoint
├── server.ts                   assembles the server (no listen — injectable from tests)
├── container.ts                DI wiring: which class satisfies which port
├── config.ts                   environment → Config
├── domain/
│   ├── model.ts                Candidate, Scorecard, Finding, Grade … no framework imports
│   └── scoring.ts              findings → scorecard: the grading policy
├── appl/
│   ├── usecase/
│   │   ├── ScoringService.ts           inbound port (+ ScoringError)
│   │   └── ScorecardScoringService.ts  the use case
│   └── out/
│       ├── ScorerDelegate.ts           outbound port: the scoring engine
│       └── SpecificationLoader.ts      outbound port: fetching a source URI
└── adapter/
    ├── in/web/
    │   ├── ScoringRestController.ts  POST /api/v1/scorings
    │   ├── HealthController.ts       GET /healthz
    │   ├── contract.ts               route schemas lifted from the OpenAPI document
    │   ├── problem.ts                RFC 9457 error rendering
    │   └── model/
    │       ├── scoring.d.ts          GENERATED — do not edit
    │       └── mapper.ts             wire DTO ↔ domain
    └── out/
        ├── spectral/                 the Spectral engine + ruleset composition
        ├── http/                     guarded specification fetching
        └── dummy/                    offline stand-in engine (the default)
```

A note on the vocabulary, since it trips people up: the **controller is the
inbound _adapter_**; the inbound **port** is `ScoringService`, the interface the
application exposes. The controller depends on that interface and resolves the
implementation from the container — it never imports `DummyScoringService`.

### Import aliases

Cross-layer imports name the layer instead of counting `../` hops:

| Alias | Layer |
|-------|-------|
| `#domain/*` | `src/domain/*` |
| `#appl/*` | `src/appl/*` |
| `#adapter/*` | `src/adapter/*` |

```ts
import { ScoringError } from '#appl/usecase/ScoringService.js';   // not '../../../appl/…'
```

Same-directory siblings stay relative (`./contract.js`) — an alias there would
only add noise.

This is configured in **two places, and they must stay in step**:

- `tsconfig.json` `paths` — what `tsc` and your editor resolve against.
- `package.json` `imports` — what **Node** resolves against at run time.

Both are required because `tsc` does not rewrite import specifiers: `#appl/…`
is emitted into `dist/` verbatim, so Node has to know what it means on its own.
Node's subpath-imports map is the native mechanism for that, which is why the
aliases are `#`-prefixed — that prefix is what makes them legal there, and it
keeps them visibly distinct from npm package names. The alternative, a
`tsc-alias` rewrite step, would buy nothing but another build dependency.

The map is conditional because the two run modes load different files:

```jsonc
"#appl/*": {
  "development": "./src/appl/*",   // npm run dev  → tsx, TypeScript sources
  "default":     "./dist/appl/*"   // npm start    → node, compiled output
}
```

Hence `--conditions=development` in the `dev` script. Forget it and `npm run
dev` silently runs whatever is in `dist/`.

Adding a layer means touching both maps plus the table above.

### Injection

`@fastify/awilix` puts an Awilix container on the app and a child scope on every
request. `container.ts` is the only file that names a concrete class:

```ts
diContainer.register({
  scoringService: asClass(DummyScoringService, { lifetime: Lifetime.SINGLETON }),
});
```

The controller resolves it per request:

```ts
const scoringService = request.diScope.resolve('scoringService');
```

`declare module '@fastify/awilix'` in `container.ts` augments the Awilix
`Cradle`, so `resolve('scoringService')` is typed as `ScoringService` rather
than `any` — a registration that does not satisfy the port fails to compile.

### Contract-first

Nothing here hand-writes the contract's shapes:

- **Types** — `npm run generate` runs `openapi-typescript` over the spec into
  `src/adapter/in/web/model/scoring.d.ts`. The file is git-ignored and
  regenerated by `predev`/`prebuild`; never edit it, edit the spec.
- **Validation** — `contract.ts` dereferences the OpenAPI document at boot and
  lifts each operation's request/response schema straight onto the Fastify
  route, keyed by `operationId`. Tightening the spec tightens the server with no
  code change. Dereferencing (rather than bundling) is what resolves the
  external `../problem/problem_rfc_v1.openapi.json` reference that Ajv could not
  follow on its own.

The document is therefore **runtime input, not just build input** — the
Dockerfile copies `api/` into the final image, and a spec that fails to load is
a startup failure rather than a 500 the first caller discovers.

### Errors

`problem.ts` renders every failure as `application/problem+json` per RFC 9457,
with `type` URIs from the SmartBear Problems Registry, matching what
api-onboarding emits:

| Case | Status | `type` |
|------|--------|--------|
| Schema validation failed | 422 | `…/validation-error` (`422-02`) |
| `ScoringError` from the application | 422 | `…/business-rule-violation` (`422-01`) |
| Malformed JSON, bad content type, … | 400 | `about:blank` |
| No matching route | 404 | `about:blank` |
| Anything unhandled | 500 | `about:blank` |

## Scoring

`SCORER_ADAPTER=spectral` puts [Spectral](https://github.com/stoplightio/spectral)
behind the `ScorerDelegate` port, running **in-process** — it is a library here,
not a service. The default is `dummy`, so the module stays deployable alone.

### Who decides what

The engine port returns **findings, not a scorecard**. That line is the whole
design:

| Decision | Lives in | Change it by |
|---|---|---|
| Which rules run, and how serious each is | the ruleset | editing `rulesets/evaluation.yaml` |
| Which dimension a rule belongs to | the ruleset | editing `rulesets/evaluation.yaml` |
| What a broken rule is worth, the grading curve | `src/domain/scoring.ts` | changing code |

So retuning *this* deployment is a config change; retuning *scoring itself* is a
code change. Verify the first half holds by demoting a rule in
`evaluation.yaml`, restarting, and re-scoring the same document — the score
moves with no rebuild.

### How a dimension is scored

`100 × (1 − damage / capacity)` — the share of a dimension's rules the document
satisfies, weighted by severity. Two details are load-bearing, and both replaced
an earlier "start at 100 and subtract" model that measured the wrong thing:

- **Each broken rule counts once**, however often it fires. Otherwise a single
  chatty rule like `oas3-parameter-description`, firing hundreds of times on a
  large document, decides the dimension by itself.
- **The denominator is the ruleset, not a constant.** Rules such as
  `owasp:api4:2023-string-limit` can only fire on a document that actually
  declares strings, so subtracting from a fixed 100 rewards documents for
  describing less. Measured against the rules in play, detail is no longer
  self-defeating.

Only dimensions with rules behind them are reported. `spectral:oas` plus OWASP
cover **FC, SEC, DX, MR**; **ARAX, AU and AUD are never emitted**, because
nothing measures them and a silent 100 would be a lie.

### Ruleset composition lives in code, not YAML

`adapter/out/spectral/RulesetFactory.ts` composes `spectral:oas` and the OWASP
ruleset programmatically. A `.spectral.yaml` with
`extends: ["@stoplight/spectral-owasp-ruleset"]` does **not** work: the bundler
resolves that package to its CommonJS build and Rollup fails with
`MISSING_EXPORT`, and importing it as ESM fails too because its own `.mjs` takes
a named import from CommonJS `@stoplight/spectral-core`. `createRequire` is the
way in.

Rules the scorer authors declare their dimension inline via Spectral's
`extensions`; inherited rules cannot (Spectral's schema requires `given` and
`then` on any rule written as an object), so they are decorated at boot from the
overlay's globs. Note that `tags` looks like it would do the same job and is even
accepted by the ruleset schema, but it never reaches the `Rule` object — use
`extensions`.

## Fetching a source URI

A `source` is always a **public HTTP(S) URL outside the cluster**, which makes
`HttpSpecificationLoader`'s guard free: it refuses nothing a real caller sends.
Without it this service is an SSRF proxy — a caller who cannot reach
`api-registry` or the node metadata endpoint could ask *us* to read them and
hand the response back inside a violation.

It rejects non-`http(s)` schemes, resolves DNS and refuses loopback, private,
link-local and CGNAT addresses, re-checks **every redirect hop** (a public URL
can redirect to `127.0.0.1`), and caps both body size and time.

Spectral's `$ref` resolver is a *second*, independent way out of the process, so
it is disabled: a document containing `$ref: http://api-registry:1337/…` is
reported as `invalid-ref` rather than fetched. Internal refs still resolve.

## What is deliberately missing

- **Tests.** Not scaffolded yet. `buildServer()` returns without listening
  precisely so they can drive it through `app.inject()`.
- **A Helm chart.** `helm/api-scorer/` still has to be written; model it on
  `helm/api-portal/`, which deploys the same way (Node server, ConfigMap
  consumed with `envFrom`, uid/gid 10001). Mount `rulesets/evaluation.yaml` from
  a ConfigMap so scoring can be tuned without a rebuild.
- **GraphQL scoring.** Spectral ships no GraphQL ruleset, so `GRAPHQLS`
  candidates are refused with a 422 rather than scored badly.

## Configuration

Read from the environment by `config.ts`:

| Variable | Default | Meaning |
|----------|---------|---------|
| `HOST` | `0.0.0.0` | Bind address — `0.0.0.0` so the kubelet reaches the probes |
| `PORT` | `3000` | Listen port |
| `LOG_LEVEL` | `info` | Pino level |
| `API_BASE_PATH` | `/api/v1` | Prefix, matching the contract's `servers[0].url` |
| `SPEC_PATH` | `api/scoring/scoring_v1.openapi.yaml` | Contract to serve, relative to the working directory |
| `SCORER_ADAPTER` | `dummy` | `dummy` or `spectral`. An unknown value fails at startup rather than silently falling back |
| `SPECTRAL_EVALUATION` | `rulesets/evaluation.yaml` | Rule → dimension mapping and severity overrides |
| `SPEC_MAX_BYTES` | `5242880` | Ceiling on a fetched specification |
| `SPEC_FETCH_TIMEOUT_MS` | `10000` | Fetch timeout |
| `SCORING_TIMEOUT_MS` | `30000` | Ceiling on one linting run |
| `MAX_VIOLATIONS` | `100` | Violations returned, most serious kept |

## Container

```bash
docker build -t api-scorer:dev .        # nerdctl --namespace k8s.io build … on containerd
docker run --rm -p 3000:3000 api-scorer:dev
```
