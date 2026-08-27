# syntax=docker/dockerfile:1.12
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY apps/api/gradle apps/api/gradle
COPY apps/api/gradlew apps/api/settings.gradle.kts apps/api/build.gradle.kts apps/api/
COPY packages/contracts/openapi.yaml packages/contracts/openapi.yaml
COPY apps/api/src apps/api/src
RUN --mount=type=cache,target=/root/.gradle \
    apps/api/gradlew -p apps/api --no-daemon bootJar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 bgstore && adduser -S -D -H -u 10001 -G bgstore bgstore
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/apps/api/build/libs/*.jar app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=12 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
