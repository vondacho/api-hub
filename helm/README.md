# Helm charts

Deployment charts for the api-hub components. Following the repo's
monorepo-of-standalone-modules convention, **each component gets its own
independent chart** — there is no umbrella chart.

| Chart | Component | Status |
|-------|-----------|--------|
| `api-onboarding/` | `api-onboarding` Spring Boot service | present |
| `api-portal/` | `api-portal` Astro Catalogue frontend | present |
| `api-registry/` | `api-registry` Strapi CMS and its admin UI | present |
| `api-registry-db/` | `api-registry-db` PostgreSQL instance | present |
| `api-scorer/` | `api-scorer` Spectral scoring service | present |

The registry is two charts rather than one, for the same reason the repo is a
monorepo of standalone modules: the CMS is redeployed on every image change and
the database is not. `api-registry` reads the database password straight from
the Secret the `api-registry-db` release owns, so the credential exists in one
place. **Install `api-registry-db` first** — Strapi migrates its schema on boot
and crash-loops until the database answers.

`api-onboarding`'s chart defaults both outbound adapters to the in-memory
**dummy**, so it stays deployable on its own — but its `values-local.yaml` now
wires the scorer to the deployed `api-scorer` for real. The registry still has
to be switched on by hand; see *Pointing at real dependencies*. `api-portal` points at `api-onboarding` over in-cluster DNS
but does not require it to be up in order to start.

> **The registry runs on PostgreSQL, not MongoDB.** Strapi 5 reaches its
> database through Knex and ships connectors for `postgres`, `mysql` and
> `sqlite` only — see the `connections` map in `api-registry/config/database.ts`.
> Mongoose support was dropped after Strapi v3. `api-registry-db/README.md`
> covers this.

## Prerequisites

- A local Kubernetes cluster. This repo's kubeconfig has `rancher-desktop`
  (current context) and `docker-desktop`.
- `helm` v3+ — Rancher Desktop ships one at `~/.rd/bin/helm`; otherwise
  `brew install helm`.
- A container build tool (`docker`, or `nerdctl` when Rancher Desktop runs
  containerd).

## 1. Build the images

The image must land in the image store that your cluster's kubelet reads,
otherwise `pullPolicy: Never` will fail with `ErrImageNeverPull`.

**Rancher Desktop with dockerd (moby), or Docker Desktop** — a plain build is
enough, the cluster shares the daemon's image store:

```bash
cd api-onboarding  && docker build -t api-onboarding:dev .
cd api-portal      && docker build -t api-portal:dev .
cd api-registry    && docker build -t api-registry:dev .
cd api-registry-db && docker build -t api-registry-db:dev .
cd api-scorer      && docker build -t api-scorer:dev .
```

**Rancher Desktop with containerd** — build into the `k8s.io` namespace instead:

```bash
cd api-onboarding  && nerdctl --namespace k8s.io build -t api-onboarding:dev .
cd api-portal      && nerdctl --namespace k8s.io build -t api-portal:dev .
cd api-registry    && nerdctl --namespace k8s.io build -t api-registry:dev .
cd api-registry-db && nerdctl --namespace k8s.io build -t api-registry-db:dev .
cd api-scorer      && nerdctl --namespace k8s.io build -t api-scorer:dev .
```

The `api-onboarding` build runs `mvn package -DskipTests -Dbundle.skip=true`.
`bundle.skip` turns off the `redocly`/`zuplo` spec bundling bound to
`process-test-resources`, which is the only part of the build needing a Node
toolchain — the contract-first client generation at `generate-sources` still
runs normally.

The `api-portal` build runs `npm ci && npm run build` on `node:22-alpine`, then
ships only the production `node_modules` plus `dist/`. Two of its inputs are
easy to drop from the Dockerfile by accident:

- **`.npmrc` is copied alongside `package.json`.** It carries
  `legacy-peer-deps`, without which `npm ci` refuses to resolve
  `@scalar/astro` (it peers on Astro 4/5; the portal is on Astro 7). Omit the
  file and the image build dies at `ERESOLVE`, even though a local
  `node_modules` built earlier works fine.
- **`scripts/` is copied too.** `prebuild` runs
  `scripts/copy-scalar-bundle.mjs`, which vendors Scalar's standalone bundle
  into `public/scalar/` so the contract viewer loads it from the portal's own
  origin instead of jsDelivr. The bundle is git-ignored and regenerated on
  every build; without the script the viewer silently falls back to the public
  CDN, which is unreachable from a closed cluster.

The `api-registry` build follows
[Strapi's containerisation guide](https://docs.strapi.io/cms/installation/docker),
taking the "selective copies" variant that page offers rather than its default
`COPY /opt/app ./`. Anything Strapi reads at run time therefore has to be listed
in the Dockerfile — including `tsconfig.json`, which is not obviously a runtime
file: `strapi start` reads its `outDir` to locate the compiled server. Without
it Strapi treats `/app` as the app root, finds no `config/`, and dies with
`Cannot destructure property 'client' of 'db.config.connection'`.

The `api-registry-db` build is stock `postgres:17-alpine` plus the SQL in
`api-registry-db/initdb/`, which runs once on first boot.

## 2. Install

Order matters for the registry pair — the CMS migrates its schema against a
database that has to already be accepting connections.

```bash
helm upgrade --install api-registry-db helm/api-registry-db \
  --namespace api-hub --create-namespace \
  -f helm/api-registry-db/values-local.yaml

helm upgrade --install api-registry helm/api-registry \
  --namespace api-hub --create-namespace \
  -f helm/api-registry/values-local.yaml

helm upgrade --install api-onboarding helm/api-onboarding \
  --namespace api-hub --create-namespace \
  -f helm/api-onboarding/values-local.yaml

helm upgrade --install api-portal helm/api-portal \
  --namespace api-hub --create-namespace \
  -f helm/api-portal/values-local.yaml

# Independent of the others — nothing else has to be up first.
helm upgrade --install api-scorer helm/api-scorer \
  --namespace api-hub --create-namespace \
  -f helm/api-scorer/values-local.yaml
```

## 3. Verify

```bash
helm test api-registry-db -n api-hub
helm test api-registry    -n api-hub
helm test api-onboarding  -n api-hub
helm test api-portal      -n api-hub
helm test api-scorer      -n api-hub
```

`api-onboarding` has no ingress by default:

```bash
kubectl -n api-hub port-forward svc/api-onboarding 8080:8080
curl http://localhost:8080/actuator/health
```

`api-portal`'s `values-local.yaml` enables a Traefik ingress, so it is reachable
straight from the host — Rancher Desktop runs Traefik as the default
IngressClass and `*.localhost` resolves to 127.0.0.1:

```bash
open http://api-portal.localhost
curl http://api-portal.localhost/healthz     # {"status":"UP"}
```

With `ingress.enabled=false` (the chart default), port-forward instead:

```bash
kubectl -n api-hub port-forward svc/api-portal 4321:4321
```

`api-registry`'s `values-local.yaml` enables a Traefik ingress too, because the
admin UI is a browser application:

```bash
open http://api-registry.localhost/admin
```

The first visit asks you to create the initial administrator. That account lives
in `api-registry-db`, so it survives a redeploy of the CMS but not a wipe of the
database volume. `api-registry-db` is deliberately **not** exposed — reach it
through the CMS, or with a shell:

```bash
kubectl -n api-hub exec -it statefulset/api-registry-db -- psql -U strapi -d strapi
```

## 4. Redeploy a component

Shipping a code change to a running cluster takes **three** steps, not two.
Rebuilding the image and running `helm upgrade` is not enough on its own:

```bash
# 1. rebuild into the store the kubelet reads (see step 1 for the containerd variant)
cd api-portal && docker build -t api-portal:dev .

# 2. reconcile the release
helm upgrade --install api-portal helm/api-portal \
  -n api-hub -f helm/api-portal/values-local.yaml

# 3. force the pods onto the new image
kubectl rollout restart deployment/api-portal -n api-hub
kubectl rollout status  deployment/api-portal -n api-hub --timeout=300s
```

**Why step 3 is not optional.** Every `values-local.yaml` pins `tag: dev` with
`pullPolicy: Never`. Rebuilding produces a new image under the *same* tag, so
the rendered Deployment is byte-identical to the one already applied —
Kubernetes sees no change, creates no ReplicaSet, and leaves the old pods
running. `helm upgrade` reports `STATUS: deployed` and bumps the revision, which
makes it look like the deploy worked. The `checksum/config` annotation on the
pod templates only covers *ConfigMap* changes; it does nothing for an image
rebuilt under a fixed tag. Skip the restart and you get a new Helm revision
serving the old code.

`api-registry-db` is a StatefulSet, so use `statefulset/api-registry-db` in
place of `deployment/...`. It is also the one component not to restart out of
habit — bouncing it takes the database down and crash-loops the CMS until it is
back.

Confirm the pod actually picked the image up, rather than trusting the rollout:

```bash
kubectl get pods -n api-hub -l app.kubernetes.io/name=api-portal \
  -o custom-columns='NAME:.metadata.name,DELETING:.metadata.deletionTimestamp,IMAGEID:.status.containerStatuses[0].imageID'
docker inspect api-portal:dev --format '{{.Id}}'
```

The digest of the row with no `DELETING` timestamp must match the local image.
Print that column rather than filtering it away: **a terminating pod still
reports `status.phase: Running`**, so `--field-selector status.phase=Running`
does not exclude it, and for a few seconds after `rollout status` returns the
old pod can still be `items[0]` — reading `items[0]` there reports the *old*
digest and makes a good deploy look like a failed one.

A full-stack redeploy is the same three steps per component, in the install
order from step 2: `api-registry-db`, `api-registry`, `api-onboarding`,
`api-portal`, `api-scorer`.

### Build engine gotcha

`nerdctl build` fails with `no buildkit host is available ... failed to ping to
host unix:///run/buildkit/buildkitd.sock` when Rancher Desktop is configured for
**moby** rather than containerd — there is no buildkitd to talk to. Check which
engine is active before reaching for either command:

```bash
grep -o '"name":"[a-z]*"' ~/Library/Preferences/rancher-desktop/settings.json
```

`moby` means `docker build`; `containerd` means `nerdctl --namespace k8s.io build`.

## Pointing at real dependencies

The registry is deployed, so `api-onboarding` can be switched off its dummy
adapter. The base URLs already default to in-cluster service DNS names —
`app.strapi.baseUrl` is `http://api-registry:1337/api`, which matches the
Service the registry chart creates:

```bash
helm upgrade --install api-onboarding helm/api-onboarding \
  -n api-hub \
  -f helm/api-onboarding/values-local.yaml \
  --set app.registry.adapter=strapi
```

Strapi's content API is closed to anonymous callers by default, and
`adapter/out/strapi` sends no API token, so the onboarding service gets `403`s
until the `specification` permissions are granted to the **Public** role under
*Settings → Users & Permissions → Roles* in the admin UI. `curl
http://api-registry.localhost/api/specifications` returning `403` is that same
default, not a broken deployment.

### Pointing api-onboarding at api-scorer

**Already done in `values-local.yaml`** — `app.scorer.adapter: spectral`. Deploy
`api-scorer` first, then `api-onboarding`, and the two are connected:

```bash
helm upgrade --install api-scorer helm/api-scorer \
  --namespace api-hub -f helm/api-scorer/values-local.yaml
helm upgrade --install api-onboarding helm/api-onboarding \
  --namespace api-hub -f helm/api-onboarding/values-local.yaml
```

`app.spectral.baseUrl` defaults to `http://api-scorer:8081/api/v1`. Both halves
of that matter: the generated `ScoringApi` appends `/scorings`, so the path must
end at the scoring contract's `servers[0].url` (`/api/v1`, not `/api`), and the
port is 8081 because `api-scorer`'s Service maps 8081 onto its container's 3000
for exactly this reason.

Submitting a candidate proves the link end to end:

```bash
kubectl -n api-hub port-forward svc/api-onboarding 8080:8080

curl -X POST http://localhost:8080/api/v1/registrations \
  -H 'content-type: application/json' \
  -d '{"source":"https://raw.githubusercontent.com/vondacho/api-hub/main/api-onboarding/src/test/resources/api/examples/oas/valid_candidate.openapi.yaml"}'
```

A `201` carrying a `scorecard` with FC/SEC/DX/MR dimensions means the call
reached the scorer. The API is served under the contract's `servers[0].url`,
carried by `RegistrationApi`'s `@RequestMapping` rather than by
`server.servlet.context-path` — a context path would prefix `/actuator` too and
break the chart's probes.

Two things about the candidate URL are not incidental, and a candidate that
misses either fails **inside onboarding, before the scorer is ever called** —
do not read those 422s as a broken link:

- `Receptionist` sniffs the contract from the document's **first line**, so the
  spec must open with `openapi: <version>`, and the version must be one
  `Contract.Version` knows — any 3.0.x, 3.1.0 or 3.2.0.
- `info.version` must match `^v[0-9][1-9]*$` — `v1`, not `1.0.0`. This is what
  rules out most public specs, including the Swagger Petstore.

The source also has to be publicly reachable, because onboarding forwards the
**URI** (not the body) and `api-scorer` refuses to fetch anything resolving to a
private address.

## How configuration reaches the apps

The charts differ, because the runtimes differ.

**api-onboarding** — `values.yaml` `app.*` renders into a ConfigMap mounted at
`/app/config/application.yaml`. Spring Boot loads the `config/` directory ahead
of the classpath, so those keys override the `application.yaml` baked into the
jar while every key left unset there still applies. Nothing needs to be
duplicated into the chart — put anything extra under `app.extraConfig`, which is
merged verbatim.

**api-portal** — `values.yaml` `app.*` renders into a ConfigMap consumed with
`envFrom`, so every key becomes an environment variable (`ONBOARDING_BASE_URL`,
plus anything under `app.extraConfig`). Astro reads these server-side during SSR;
they only reach the browser if a page renders them explicitly.

**api-scorer** — same `envFrom` pattern as the portal; every `app.*` key becomes
an environment variable read by `src/config.ts`. Its one extra is
`app.evaluation.content`: set it and the chart renders a *second* ConfigMap
holding `evaluation.yaml` — the rule-to-dimension mapping and severity overrides
— mounts it at `/app/config/rulesets`, and points `SPECTRAL_EVALUATION` at it.
That is how scoring is retuned without rebuilding the image. It **replaces** the
file baked into the image rather than merging with it, so start from a copy of
`api-scorer/rulesets/evaluation.yaml`; a document missing its `dimensions`
section would silently collapse every rule into `FC`. It is mounted as a
directory, not a `subPath`, because `subPath` mounts never see ConfigMap
updates.

All of these charts annotate the pod template with a `checksum/config`, so a
config change rolls the pods.

**api-registry** — `values.yaml` renders into a ConfigMap consumed with
`envFrom`, read by `api-registry/config/*.ts` through Strapi's `env()` helper.
Only non-secret keys go there. Credentials arrive as two separate Secrets, and
neither is duplicated into `values.yaml`:

- Strapi's own six (`APP_KEYS`, `JWT_SECRET`, `ENCRYPTION_KEY`, …) are generated
  by the chart on first install and **read back from the live Secret on every
  upgrade**. That is not cosmetic: rotating `ENCRYPTION_KEY` makes already
  stored encrypted fields unreadable, and rotating `APP_KEYS` or
  `ADMIN_JWT_SECRET` signs every administrator out.
- `DATABASE_PASSWORD` is a `secretKeyRef` into the Secret owned by the
  `api-registry-db` release, so the two charts cannot drift apart.

**api-registry-db** — no ConfigMap. `POSTGRES_DB` and `POSTGRES_USER` are plain
env vars and `POSTGRES_PASSWORD` comes from its Secret, generated and preserved
by the same read-back rule. Everything else is baked into the image, because a
database's encoding and bootstrap belong with the image rather than the release.

## Notes on the api-registry charts

- **PostgreSQL, not MongoDB** — see the note at the top of this file.
- The database is a **StatefulSet**: exactly one PostgreSQL process may open the
  data directory, and a Deployment's rolling update would start a second one
  against it.
- `PGDATA` points at a *subdirectory* of the volume mount. `initdb` refuses a
  directory it does not own at `0700`/`0750`, which is exactly what a
  PersistentVolume mounted with an `fsGroup` looks like; letting `initdb` create
  the subdirectory itself sidesteps the check.
- Probes exec `pg_isready` rather than opening a TCP socket — a TCP check passes
  while the cluster is still recovering, and would let Strapi connect too early.
- The CMS ingress is routed at `/`, not `/admin`. The admin bundle loads from
  `/admin` but calls the content API at `/api` and uploads at `/uploads`, so a
  narrower prefix breaks the UI.
- `app.url` must match the ingress host. Strapi hands that origin to the admin
  panel for its own asset and API calls; left empty the UI calls back to the
  in-cluster address, which no browser can resolve. The chart derives it from
  the first ingress host when unset.
- `readOnlyRootFilesystem: true` is on for both. Strapi additionally gets an
  `emptyDir` at `/home/app` — the CLI creates a config store under `$HOME` while
  loading its commands and logs `Failed to load command` if it cannot.
- The uploads PVC is `ReadWriteOnce`, so the CMS Deployment uses the `Recreate`
  strategy; a rolling update would deadlock waiting for the old pod to release
  the volume.
- Both the uploads PVC and the two Secrets carry
  `helm.sh/resource-policy: keep`. Uploaded media is referenced by rows in the
  database, and `ENCRYPTION_KEY` decrypts them, so neither should be easier to
  destroy than the data it belongs to.

## Notes on the api-portal chart

- The portal is **server-rendered** (`output: 'server'` + `@astrojs/node` in
  standalone mode). That is what lets it call `api-onboarding` at
  `http://api-onboarding:8080` from inside the cluster; a static build could
  only reach it from the browser, which would mean exposing the onboarding
  service publicly and dealing with CORS.
- Probes hit `/healthz`, served by `api-portal/src/pages/healthz.ts`.
- `@astrojs/node` enables filesystem-backed sessions and bakes their location
  (`/app/node_modules/.astro/sessions`) into the server bundle at build time.
  The chart mounts an `emptyDir` there because `readOnlyRootFilesystem: true` is
  on — the directory is created lazily, so the first page to use `Astro.session`
  would otherwise be the one that fails.

## Chart layout

Every chart follows the same shape; the registry pair adds what stateful
components need.

```
api-onboarding/                 api-portal/                  api-registry/                 api-registry-db/
  Chart.yaml                      Chart.yaml                   Chart.yaml                    Chart.yaml
  values.yaml                     values.yaml                  values.yaml                   values.yaml            # defaults
  values-local.yaml               values-local.yaml            values-local.yaml             values-local.yaml      # local-cluster overrides
  templates/                      templates/                   templates/                    templates/
    _helpers.tpl                    _helpers.tpl                 _helpers.tpl                  _helpers.tpl
    configmap.yaml                  configmap.yaml               configmap.yaml                -                    # app config
    -                               -                            secret.yaml                   secret.yaml          # generated, preserved on upgrade
    deployment.yaml                 deployment.yaml              deployment.yaml               statefulset.yaml
    -                               -                            pvc.yaml                      -                    # db uses a volumeClaimTemplate
    service.yaml                    service.yaml                 service.yaml                  service.yaml
    serviceaccount.yaml             serviceaccount.yaml          serviceaccount.yaml           serviceaccount.yaml
    ingress.yaml                    ingress.yaml                 ingress.yaml                  -                    # on in values-local.yaml
    NOTES.txt                       NOTES.txt                    NOTES.txt                     NOTES.txt
    tests/test-connection.yaml      tests/test-connection.yaml   tests/test-connection.yaml    tests/test-connection.yaml
```

`api-scorer/` follows the `api-portal/` column exactly, plus one file:
`templates/evaluation-configmap.yaml`, rendered only when
`app.evaluation.content` is set. Its `tests/test-connection.yaml` holds two
pods — a `/healthz` probe and one that actually posts a candidate, because a
scorer that boots with an unusable ruleset still passes a health check.
