# syntax=docker/dockerfile:1
#
# Multi-stage build for the api-scorer Fastify service.
#
# Two directories of data ship in the runtime image on purpose, because both are
# read at boot rather than compiled in:
#   api/       routes derive their request/response schemas from the OpenAPI
#              document (see adapter/in/web/contract.ts);
#   rulesets/  the scoring engine reads its dimension and severity overlay from
#              evaluation.yaml (see adapter/out/spectral/RulesetFactory.ts).

# ---------- build ----------
FROM node:22-alpine AS build
WORKDIR /app

# Install against the lockfile alone first, so this layer stays cached until a
# dependency actually changes.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY tsconfig.json ./
COPY api ./api
COPY rulesets ./rulesets
COPY src ./src
# `prebuild` regenerates the contract types before tsc runs — the generated
# file is git-ignored, so it never arrives from the build context.
RUN npm run build

# Re-resolve without devDependencies so only the runtime tree moves forward.
RUN --mount=type=cache,target=/root/.npm npm ci --omit=dev --no-audit --no-fund

# ---------- runtime ----------
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production

# Run unprivileged; the chart's securityContext expects uid/gid 10001.
# 1000 is deliberately avoided — the stock node image already has "node" there.
RUN addgroup -g 10001 app \
 && adduser -u 10001 -G app -D -H -s /sbin/nologin app

COPY --from=build --chown=app:app /app/node_modules ./node_modules
COPY --from=build --chown=app:app /app/dist ./dist
COPY --from=build --chown=app:app /app/api ./api
COPY --from=build --chown=app:app /app/rulesets ./rulesets
COPY --from=build --chown=app:app /app/package.json ./package.json

USER app

# 0.0.0.0 so the kubelet can reach the probes; both are overridable from the chart.
ENV HOST=0.0.0.0 PORT=3000
EXPOSE 3000

CMD ["node", "./dist/main.js"]
