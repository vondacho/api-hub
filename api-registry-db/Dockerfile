# syntax=docker/dockerfile:1
#
# api-registry-db — the PostgreSQL instance backing the api-registry Strapi CMS.
#
# Why this module exists: out of the box the registry persists to an in-file
# SQLite database (`api-registry/.tmp/data.db`). That file lives inside the
# container's filesystem, so it dies with the pod and cannot be shared by more
# than one replica. Moving persistence into a standalone database turns the
# registry into a stateless workload and lets the data outlive it.
#
# Why PostgreSQL and not MongoDB: Strapi 5's data layer is Knex-based and ships
# connectors for postgres, mysql and sqlite only — see the `connections` map in
# api-registry/config/database.ts. Mongoose support was dropped after Strapi v3
# and never returned, so a document store is not an option for this component.
#
# The image is stock PostgreSQL plus this repo's initialisation, so the cluster
# bootstrap is versioned alongside the code instead of living in a hand-run
# psql session.

FROM postgres:17-alpine

# Pin encoding and collation regardless of the host locale — Strapi sorts and
# compares text in the database, and a locale drift between environments shows
# up as differently ordered catalogue results.
ENV LANG=en_US.utf8 \
    POSTGRES_INITDB_ARGS="--encoding=UTF8 --locale=C"

# Defaults for the database the registry connects to. The password is
# deliberately *not* set here: the image must not carry a credential. It is
# supplied at run time via POSTGRES_PASSWORD, which the chart wires to a Secret.
ENV POSTGRES_DB=strapi \
    POSTGRES_USER=strapi

# initdb refuses to run in a directory it does not own with 0700/0750
# permissions. A Kubernetes PersistentVolume arrives owned by root with an
# fsGroup bit set, which fails that check — so point PGDATA at a *subdirectory*
# of the mount, which initdb creates itself with the ownership and mode it wants.
ENV PGDATA=/var/lib/postgresql/data/pgdata

# Executed by the entrypoint on first boot only, in filename order, once the
# cluster exists.
COPY initdb/ /docker-entrypoint-initdb.d/

EXPOSE 5432

# Sized for a local single-node cluster with one registry replica; the stock
# defaults assume a dedicated database host.
CMD ["postgres", "-c", "max_connections=50", "-c", "shared_buffers=128MB"]
