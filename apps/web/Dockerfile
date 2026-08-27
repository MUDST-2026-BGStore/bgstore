# syntax=docker/dockerfile:1.12
FROM node:24.19.0-alpine AS build
WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@10.33.0 --activate
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml nx.json tsconfig*.json ./
COPY apps/web/package.json apps/web/package.json
COPY apps/web-e2e/package.json apps/web-e2e/package.json
COPY packages/contracts/package.json packages/contracts/package.json
# Git hooks belong on developer workstations, not in the hermetic image build.
RUN --mount=type=cache,target=/pnpm/store pnpm install --frozen-lockfile --ignore-scripts --store-dir=/pnpm/store
COPY . .
RUN pnpm --filter @mudst-2026-bgstore/web build

FROM nginxinc/nginx-unprivileged:1.29.5-alpine
COPY apps/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/apps/web/dist /usr/share/nginx/html
USER 101:101
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=10 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
