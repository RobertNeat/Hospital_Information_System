ARG RUNTIME_VERSION
FROM 192.168.1.162:5000/library/node:${RUNTIME_VERSION}-alpine AS build
RUN apk upgrade --no-cache
ARG PROJECT_PATH
ARG PACKAGE_NAME
WORKDIR /workspace
# corepack downloads the pinned package manager from registry.npmjs.org by default,
# ignoring .npmrc; point it at the local registry too.
ENV COREPACK_NPM_REGISTRY=http://192.168.1.163
RUN corepack enable
ENV npm_config_store_dir=/pnpm/store
COPY . .
# pnpm 11 only reads auth/registry settings from .npmrc; minimum-release-age and the
# fetch-retry settings below must be passed as CLI --config overrides instead (.npmrc
# or ENV vars are silently ignored for them). --config.minimum-release-age=0 disables
# pnpm 11's default supply-chain age check for this install only: that check fetches
# publish-time metadata for every lockfile entry, which is unreliable from inside a
# Docker build; the real policy gate already runs on the runner in check_node.sh
# (_job_check.yaml), a dependency of the image build. The fetch-retry overrides widen
# tolerance for the registry's slow/flaky responses seen from Docker Desktop on Windows.
RUN --mount=type=cache,id=pnpm-store,target=/pnpm/store \
    pnpm --config.minimum-release-age=0 \
         --config.fetch-timeout=300000 \
         --config.fetch-retries=5 \
         --config.fetch-retry-mintimeout=10000 \
         --config.fetch-retry-maxtimeout=120000 \
         install --frozen-lockfile \
    && pnpm --filter "${PACKAGE_NAME}..." build

FROM 192.168.1.162:5000/library/nginx:1.29-alpine
RUN apk upgrade --no-cache
ARG BUILD_OUTPUT
ARG SERVER_CONFIG
ARG PROJECT_PATH
ARG APP_PORT
COPY --from=build /workspace/${BUILD_OUTPUT} /usr/share/nginx/html
COPY ${SERVER_CONFIG} /etc/nginx/conf.d/default.conf
EXPOSE ${APP_PORT}
