FROM node:26.10.0-bookworm-slim AS contract
WORKDIR /workspace
COPY package.json ./
# The CLI is a self-contained package; use the same exact version as local tooling.
RUN npm install --global --ignore-scripts "@redocly/cli@$(node -p "require('./package.json').devDependencies['@redocly/cli']")"
COPY redocly.yaml .redocly.lint-ignore.yaml ./
COPY openapi/ openapi/
RUN redocly lint theparlor --lint-config=error \
    && redocly bundle theparlor --output theparlor.openapi.yaml --lint-config=error

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY apps/api/ apps/api/
COPY --from=contract /workspace/theparlor.openapi.yaml /workspace/theparlor.openapi.yaml
WORKDIR /workspace/apps/api
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar -x test -PopenApiSpec=/workspace/theparlor.openapi.yaml

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --gid 10001 theparlor && useradd --uid 10001 --gid theparlor --no-create-home theparlor
WORKDIR /app
COPY --from=build /workspace/apps/api/build/libs/*-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
