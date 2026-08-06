# api-registry-db

The PostgreSQL instance that `api-registry` (Strapi) persists to.

## Why it exists

A stock Strapi project writes to an in-file SQLite database at
`api-registry/.tmp/data.db`. In a container that file is part of the pod's
filesystem: it is lost on every restart and cannot be shared between replicas.
This module supplies an external database instead, so the registry becomes a
stateless workload and the registered specs outlive it.

## Why PostgreSQL

Strapi 5 talks to its database through Knex and ships three connectors —
`postgres`, `mysql`, `sqlite` (see the `connections` map in
`api-registry/config/database.ts`). MongoDB was supported only in Strapi v3,
through `strapi-connector-mongoose`, and was removed in v4; there is no
Strapi 5 build that can open a Mongo connection. PostgreSQL is the connector
Strapi documents for production, so it is what this module provides.

## What the image adds to stock `postgres:17-alpine`

| Concern | Handling |
|---|---|
| Encoding / collation | `UTF8` + `--locale=C`, so text ordering is identical everywhere. |
| `PGDATA` | Points at a **subdirectory** of the volume mount. `initdb` rejects a directory it does not own at `0700`/`0750`, and a Kubernetes PV arrives root-owned with an `fsGroup` bit — letting `initdb` create the subdirectory itself sidesteps that. |
| Bootstrap | `initdb/10-strapi.sql` pins the database timezone to UTC and installs `pg_trgm`. It creates **no** application tables; Strapi migrates its own schema at startup. |
| Credential | Not baked in. `POSTGRES_PASSWORD` is injected at run time from a Secret. |

## Build

```bash
cd api-registry-db && docker build -t api-registry-db:dev .
```

On a Rancher Desktop that runs containerd rather than dockerd, build into the
namespace the kubelet reads:

```bash
nerdctl --namespace k8s.io build -t api-registry-db:dev .
```

## Run standalone

```bash
docker run --rm -e POSTGRES_PASSWORD=strapi -p 5432:5432 api-registry-db:dev
psql postgres://strapi:strapi@localhost:5432/strapi -c '\dx'
```

## Deploy

Via its own chart — see `helm/api-registry-db/` and `helm/README.md`.

```bash
helm upgrade --install api-registry-db helm/api-registry-db \
  -n my-api-portal --create-namespace -f helm/api-registry-db/values-local.yaml
```

The chart generates the password on first install and keeps it across upgrades.
`api-registry` reads it from the same Secret rather than holding a copy.
