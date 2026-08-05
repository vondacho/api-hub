# Helm charts

Deployment charts for the my-api-portal components. Following the repo's
monorepo-of-standalone-modules convention, **each component gets its own
independent chart** — there is no umbrella chart.

| Chart | Component | Status |
|-------|-----------|--------|
| `api-onboarding/` | `api-onboarding` Spring Boot service | present |
| `api-portal/` | `api-portal` Astro Catalogue frontend | present |
| `api-registry/` | Strapi CMS | planned |
| `api-scorer/` | Spectral/Jentic scoring engine | planned |

Until the registry and scorer charts exist, `api-onboarding` runs against its
in-memory **dummy** adapters (`registry.adapter=dummy`, `scorer.adapter=dummy`),
so it is fully deployable on its own. `api-portal` points at `api-onboarding`
over in-cluster DNS but does not require it to be up in order to start.

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
cd api-onboarding && docker build -t api-onboarding:dev .
cd api-portal     && docker build -t api-portal:dev .
```

**Rancher Desktop with containerd** — build into the `k8s.io` namespace instead:

```bash
cd api-onboarding && nerdctl --namespace k8s.io build -t api-onboarding:dev .
cd api-portal     && nerdctl --namespace k8s.io build -t api-portal:dev .
```

The `api-onboarding` build runs `mvn package -DskipTests -Dbundle.skip=true`.
`bundle.skip` turns off the `redocly`/`zuplo` spec bundling bound to
`process-test-resources`, which is the only part of the build needing a Node
toolchain — the contract-first client generation at `generate-sources` still
runs normally.

The `api-portal` build runs `npm ci && npm run build` on `node:22-alpine`, then
ships only the production `node_modules` plus `dist/`.

## 2. Install

```bash
helm upgrade --install api-onboarding helm/api-onboarding \
  --namespace my-api-portal --create-namespace \
  -f helm/api-onboarding/values-local.yaml

helm upgrade --install api-portal helm/api-portal \
  --namespace my-api-portal --create-namespace \
  -f helm/api-portal/values-local.yaml
```

## 3. Verify

```bash
helm test api-onboarding -n my-api-portal
helm test api-portal     -n my-api-portal
```

`api-onboarding` has no ingress by default:

```bash
kubectl -n my-api-portal port-forward svc/api-onboarding 8080:8080
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
kubectl -n my-api-portal port-forward svc/api-portal 4321:4321
```

## Pointing at real dependencies

Once the registry/scorer charts are deployed, flip the adapters. The base URLs
default to in-cluster service DNS names:

```bash
helm upgrade --install api-onboarding helm/api-onboarding \
  -n my-api-portal \
  -f helm/api-onboarding/values-local.yaml \
  --set app.registry.adapter=strapi \
  --set app.scorer.adapter=spectral
```

## How configuration reaches the apps

The two charts differ, because the runtimes differ.

**api-onboarding** — `values.yaml` `app.*` renders into a ConfigMap mounted at
`/app/config/application.yaml`. Spring Boot loads the `config/` directory ahead
of the classpath, so those keys override the `application.yaml` baked into the
jar while every key left unset there still applies. Nothing needs to be
duplicated into the chart — put anything extra under `app.extraConfig`, which is
merged verbatim.

**api-portal** — `values.yaml` `app.*` renders into a ConfigMap consumed with
`envFrom`, so every key becomes an environment variable (`ONBOARDING_BASE_URL`,
plus anything under `app.extraConfig`). Astro reads these server-side during SSR;
they only reach the browser if a page renders them explicitly. Both charts
annotate the pod template with a `checksum/config`, so a config change rolls the
pods.

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

Both charts follow the same shape:

```
api-onboarding/                 api-portal/
  Chart.yaml                      Chart.yaml
  values.yaml                     values.yaml            # defaults
  values-local.yaml               values-local.yaml      # local-cluster overrides
  templates/                      templates/
    _helpers.tpl                    _helpers.tpl
    configmap.yaml                  configmap.yaml       # app config
    deployment.yaml                 deployment.yaml
    service.yaml                    service.yaml
    serviceaccount.yaml             serviceaccount.yaml
    ingress.yaml                    ingress.yaml         # on in values-local.yaml
    NOTES.txt                       NOTES.txt
    tests/test-connection.yaml      tests/test-connection.yaml
```
