FROM node:24.13.0-trixie-slim AS builder
WORKDIR /home/node/app/

# Install libjemalloc2 here so it can be copied to the runtime stage without
# running apt-get update/upgrade in the runtime image.
RUN apt-get update && \
    apt-get install -y --no-install-recommends libjemalloc2 && \
    rm -rf /var/lib/apt/lists/*

# Install all dependencies (including devDependencies for esbuild)
COPY package*.json ./
RUN npm config set update-notifier false && \
    npm ci

# Build the bundle
COPY . ./
RUN npm run build

FROM node:24.13.0-trixie-slim
LABEL maintainer="mirrornode@hedera.com"

# Setup
ENV DEBIAN_FRONTEND=noninteractive
ENV NODE_ENV=production
EXPOSE 5551
# Node 24 has a built-in fetch API, so no wget/curl needed for the health check.
HEALTHCHECK --interval=10s --retries=3 --start-period=25s --timeout=2s \
  CMD node -e "fetch('http://localhost:5551/health/liveness').then(r=>r.ok?process.exit(0):process.exit(1)).catch(()=>process.exit(1))"
WORKDIR /home/node/app/

# Copy the bundled application output and static assets needed at runtime.
# dist/package.json is a minimal manifest listing only the 3 externalized packages;
# combined with the lock file it gives a reproducible, much smaller npm ci install.
COPY package-lock.json ./
COPY --from=builder --chown=node:node /home/node/app/dist ./dist
RUN npm config set update-notifier false && \
    cp dist/package.json package.json && \
    npm install --omit=dev && \
    npm cache clean --force --loglevel=error && \
    chown -R node:node .
# openapiHandler.js resolves api/v1/openapi.yml via process.cwd(), so api/ must be at the app root
COPY --from=builder --chown=node:node /home/node/app/api ./api

# Copy libjemalloc from the builder stage and configure it as a preloaded library.
# Using --mount=type=bind because COPY does not support shell variable expansion
# needed for the architecture-specific library path ($(uname -m)-linux-gnu).
RUN --mount=type=bind,from=builder,source=/usr/lib,target=/builder-lib \
    cp /builder-lib/$(uname -m)-linux-gnu/libjemalloc.so.2 /usr/lib/$(uname -m)-linux-gnu/ && \
    echo /usr/lib/$(uname -m)-linux-gnu/libjemalloc.so.2 >> /etc/ld.so.preload

USER node

# No extensionless/register needed — esbuild resolves extensionless imports at build time
ENTRYPOINT ["node", "dist/server.js"]