# syntax=docker/dockerfile:1
#
# Multi-stage build for the api-registry Strapi CMS.
#
# Follows https://docs.strapi.io/cms/installation/docker, with the "selective
# copies" variant that page offers instead of its default `COPY /opt/app ./`.
# Copying the whole app would also drag the TypeScript sources and the dev
# toolchain into the runtime image; the trade-off is that anything Strapi reads
# at run time has to be listed explicitly below — see the tsconfig.json note.
#
# The docs also install vips-dev for sharp. That is not needed here: sharp
# resolves a prebuilt libvips for musl (verify with
# `docker run --rm --entrypoint node api-registry:dev -e 'console.log(require("sharp").versions.vips)'`).
#
# `strapi build` does two things: it compiles the TypeScript server into dist/,
# and it bundles the React admin panel into dist/build. Both outputs are needed
# at run time, and Strapi resolves its dependencies from node_modules rather
# than bundling them, so the production tree ships with the image.
#
# WORKDIR is /app in both stages on purpose — Strapi records absolute paths
# during the admin build, so the build and runtime directories have to line up.

# ---------- build ----------
FROM node:22-alpine AS build
WORKDIR /app

# better-sqlite3 is a native module and has no prebuilt binary for alpine's
# musl, so it is compiled here. It stays in the dependency set because it is
# what `npm run develop` uses locally; in the cluster the registry talks to
# api-registry-db over the pg driver instead.
RUN apk add --no-cache python3 make g++

# Install against the lockfile alone first, so this layer stays cached until a
# dependency actually changes.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY tsconfig.json favicon.png ./
COPY config ./config
COPY src ./src
COPY public ./public
COPY types ./types
COPY database ./database

# NODE_ENV=production here so the admin panel is built minified and without the
# dev-only tooling. Telemetry is off: the build must not need the network.
ENV NODE_ENV=production \
    STRAPI_TELEMETRY_DISABLED=true \
    STRAPI_DISABLE_UPDATE_NOTIFICATION=true
RUN npm run build

# Re-resolve without devDependencies so only the runtime tree moves forward.
RUN --mount=type=cache,target=/root/.npm npm ci --omit=dev --no-audit --no-fund

# ---------- runtime ----------
FROM node:22-alpine AS runtime
WORKDIR /app

ENV NODE_ENV=production \
    STRAPI_TELEMETRY_DISABLED=true \
    STRAPI_DISABLE_UPDATE_NOTIFICATION=true

# Run unprivileged; the chart's securityContext expects uid/gid 10001.
# 1000 is deliberately avoided — the stock node image already has "node" there.
# The home directory is real, not -H: the CLI creates a config store under it
# on boot and aborts command loading if the path cannot be made.
RUN addgroup -g 10001 app \
 && adduser -u 10001 -G app -D -h /home/app -s /sbin/nologin app

COPY --from=build --chown=app:app /app/node_modules ./node_modules
COPY --from=build --chown=app:app /app/dist ./dist
COPY --from=build --chown=app:app /app/.strapi ./.strapi
COPY --from=build --chown=app:app /app/public ./public
COPY --from=build --chown=app:app /app/database ./database
COPY --from=build --chown=app:app /app/package.json /app/favicon.png ./

# Required at run time, not just at build time. `strapi start` reads tsconfig.json
# to locate the compiled output — see resolveOutDir in @strapi/typescript-utils.
# Without it Strapi treats /app itself as the app root, finds no config/ there,
# and dies with "Cannot destructure property 'client' of 'db.config.connection'".
# It is only read for its outDir; the compiler itself is not needed here.
COPY --from=build --chown=app:app /app/tsconfig.json ./tsconfig.json

# Strapi writes here at run time: uploads land in public/uploads, and the
# runtime keeps scratch state under .strapi. Both are chowned above; the chart
# additionally backs public/uploads with a volume so files survive a restart.
USER app

# Strapi reads HOST/PORT. 0.0.0.0 so the kubelet can reach the probes; both are
# overridable from the chart.
ENV HOST=0.0.0.0 PORT=1337
EXPOSE 1337

# For `docker run`; Kubernetes ignores this and uses the chart's probes, which
# hit the same endpoint.
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:1337/_health || exit 1

# `strapi start` runs the already-built server — it never rebuilds, unlike
# `strapi develop`.
CMD ["npm", "run", "start"]
