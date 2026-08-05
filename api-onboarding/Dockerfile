# syntax=docker/dockerfile:1
#
# Multi-stage build for the api-onboarding Spring Boot service.
#
# The `generate-sources` phase still runs (the Strapi/Scorer clients are needed to
# compile), but the `process-test-resources` spec bundling is skipped via
# -Dbundle.skip=true so that no Node/npx toolchain is required inside the image.

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Resolve dependencies against the pom alone first, so this layer is cached
# until the pom actually changes.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline -DskipTests -Dbundle.skip=true || true

COPY scripts ./scripts
COPY src ./src
RUN chmod +x scripts/*.sh

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B clean package -DskipTests -Dbundle.skip=true

# Split the fat jar into layers so that dependency layers stay cached across
# application-code changes.
RUN java -Djarmode=tools -jar target/api-onboarding-*.jar extract --layers --launcher --destination extracted

# ---------- runtime ----------
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Run unprivileged; the chart's securityContext expects uid/gid 10001.
# 1000 is deliberately avoided — the Ubuntu-based temurin image already has an
# "ubuntu" user there.
RUN groupadd --gid 10001 app \
 && useradd --uid 10001 --gid 10001 --create-home --shell /usr/sbin/nologin app

COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./

USER app
EXPOSE 8080

# Container-aware heap sizing; overridable from the chart via JAVA_TOOL_OPTIONS.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
