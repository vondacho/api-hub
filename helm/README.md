# Helm charts

Deployment charts for the my-api-portal components. Following the repo's
monorepo-of-standalone-modules convention, **each component gets its own
independent chart** — there is no umbrella chart.

| Chart | Component | Status |
|-------|-----------|--------|
| `api-onboarding/` | `api-onboarding` Spring Boot service | present |
| `api-portal/` | `api-portal` Astro Catalogue frontend | present |
| `api-registry/` | `api-registry` Strapi CMS and its admin UI | present |
| `api-registry-db/` | `api-registry-db` PostgreSQL instance | present |
| `api-scorer/` | Spectral/Jentic scoring engine | planned |

The registry is two charts rather than one, for the same reason the repo is a
monorepo of standalone modules: the CMS is redeployed on every image change and
the database is not. `api-registry` reads the database password straight from
the Secret the `api-registry-db` release owns, so the credential exists in one
place. **Install `api-registry-db` first** — Strapi migrates its schema on boot
and crash-loops until the database answers.

`api-onboarding` still defaults its scorer to the in-memory **dummy** adapter
because no scorer chart exists yet, so it remains deployable on its own. Its
`registry.adapter` can now be pointed at the deployed Strapi — see *Pointing at
real dependencies*. `api-portal` points at `api-onboarding` over in-cluster DNS
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
```

**Rancher Desktop with containerd** — build into the `k8s.io` namespace instead:

```bash
cd api-onboarding  && nerdctl --namespace k8s.io build -t api-onboarding:dev .
cd api-portal      && nerdctl --namespace k8s.io build -t api-portal:dev .
cd api-registry    && nerdctl --namespace k8s.io build -t api-registry:dev .
cd api-registry-db && nerdctl --namespace k8s.io build -t api-registry-db:dev .
```

The `api-onboarding` build runs `mvn package -DskipTests -Dbundle.skip=true`.
`bundle.skip` turns off the `redocly`/`zuplo` spec bundling bound to
`process-test-resources`, which is the only part of the build needing a Node
toolchain — the contract-first client generation at `generate-sources` still
runs normally.

The `api-portal` build runs `npm ci && npm run build` on `node:22-alpine`, then
ships only the production `node_modules` plus `dist/`.

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
  --namespace my-api-portal --create-namespace \
  -f helm/api-registry-db/values-local.yaml

helm upgrade --install api-registry helm/api-registry \
  --namespace my-api-portal --create-namespace \
  -f helm/api-registry/values-local.yaml

helm upgrade --install api-onboarding helm/api-onboarding \
  --namespace my-api-portal --create-namespace \
  -f helm/api-onboarding/values-local.yaml

helm upgrade --install api-portal helm/api-portal \
  --namespace my-api-portal --create-namespace \
  -f helm/api-portal/values-local.yaml
```

## 3. Verify

```bash
helm test api-registry-db -n my-api-portal
helm test api-registry    -n my-api-portal
helm test api-onboarding  -n my-api-portal
helm test api-portal      -n my-api-portal
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
kubectl -n my-api-portal exec -it statefulset/api-registry-db -- psql -U strapi -d strapi
```

## Pointing at real dependencies

The registry is deployed, so `api-onboarding` can be switched off its dummy
adapter. The base URLs already default to in-cluster service DNS names —
`app.strapi.baseUrl` is `http://api-registry:1337/api`, which matches the
Service the registry chart creates:

```bash
helm upgrade --install api-onboarding helm/api-onboarding \
  -n my-api-portal \
  -f helm/api-onboarding/values-local.yaml \
  --set app.registry.adapter=strapi
```

Strapi's content API is closed to anonymous callers by default, and
`adapter/out/strapi` sends no API token, so the onboarding service gets `403`s
until the `specification` permissions are granted to the **Public** role under
*Settings → Users & Permissions → Roles* in the admin UI. `curl
http://api-registry.localhost/api/specifications` returning `403` is that same
default, not a broken deployment. The scorer adapter stays on `dummy` until that
chart exists.

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
they only reach the browser if a page renders them explicitly. All three of
these charts annotate the pod template with a `checksum/config`, so a config
change rolls the pods.

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
