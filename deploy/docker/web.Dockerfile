FROM node:26.10.0-bookworm-slim AS build
RUN npm install --global pnpm@10.33.0
WORKDIR /workspace
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/package.json apps/web/package.json
COPY apps/web-e2e/package.json apps/web-e2e/package.json
COPY tools/backend-tools/package.json tools/backend-tools/package.json
COPY libs/web/ libs/web/
RUN pnpm install --frozen-lockfile
COPY . .
ARG WEB_MODE=production
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL NX_DAEMON=false NX_ISOLATE_PLUGINS=false
RUN pnpm nx build web --mode=$WEB_MODE

FROM nginxinc/nginx-unprivileged:1.28.0-alpine AS runtime-base
COPY deploy/docker/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080

FROM runtime-base AS artifacts-runtime
# Host builds may use umask 077. The serving user must own those private files.
COPY --from=artifacts --chown=nginx:nginx / /usr/share/nginx/html

FROM runtime-base AS runtime
COPY --from=build /workspace/apps/web/dist /usr/share/nginx/html
