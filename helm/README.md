# Helm charts

Deployment charts for the my-api-portal components. Following the repo's
monorepo-of-standalone-modules convention, **each component gets its own
independent chart** — there is no umbrella chart.

| Chart | Component | Status |
|-------|-----------|--------|
| `api-onboarding/` | `api-onboarding` Spring Boot service | present |
| `api-registry/` | Strapi CMS | planned |
| `api-scorer/` | Spectral/Jentic scoring engine | planned |

Until the registry and scorer charts exist, `api-onboarding` runs against its
in-memory **dummy** adapters (`registry.adapter=dummy`, `scorer.adapter=dummy`),
so it is fully deployable on its own.

## Prerequisites

- A local Kubernetes cluster. This repo's kubeconfig has `rancher-desktop`
  (current context) and `docker-desktop`.
- `helm` v3 — **not currently installed**: `brew install helm`.
- A container build tool (`docker`, or `nerdctl` when Rancher Desktop runs
  containerd).

## 1. Build the image

The image must land in the image store that your cluster's kubelet reads,
otherwise `pullPolicy: Never` will fail with `ErrImageNeverPull`.

**Rancher Desktop with containerd** — build into the `k8s.io` namespace:

```bash
cd api-onboarding
nerdctl --namespace k8s.io build -t api-onboarding:dev .
```

**Rancher Desktop with dockerd, or Docker Desktop** — a plain build is enough,
the cluster shares the daemon's image store:

```bash
cd api-onboarding
docker build -t api-onboarding:dev .
```

The build runs `mvn package -DskipTests -Dbundle.skip=true`. `bundle.skip`
turns off the `redocly`/`zuplo` spec bundling bound to `process-test-resources`,
which is the only part of the build needing a Node toolchain — the contract-first
client generation at `generate-sources` still runs normally.

## 2. Install

```bash
helm upgrade --install api-onboarding helm/api-onboarding \
  --namespace my-api-portal --create-namespace \
  -f helm/api-onboarding/values-local.yaml
```

## 3. Verify

```bash
helm test api-onboarding -n my-api-portal
kubectl -n my-api-portal port-forward svc/api-onboarding 8080:8080
curl http://localhost:8080/actuator/health
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

## How configuration reaches the app

`values.yaml` `app.*` renders into a ConfigMap mounted at
`/app/config/application.yaml`. Spring Boot loads the `config/` directory ahead
of the classpath, so those keys override the `application.yaml` baked into the
jar while every key left unset there still applies. Nothing needs to be
duplicated into the chart — put anything extra under `app.extraConfig`, which is
merged verbatim.

## Chart layout

```
api-onboarding/
  Chart.yaml
  values.yaml              # defaults
  values-local.yaml        # local-cluster overrides
  templates/
    _helpers.tpl
    configmap.yaml         # application.yaml overlay
    deployment.yaml
    service.yaml
    serviceaccount.yaml
    ingress.yaml           # disabled by default
    NOTES.txt
    tests/test-connection.yaml
```
