FROM node:24.13.0-trixie-slim AS builder
WORKDIR /home/node/app/

# Install libjemalloc2 here so it can be copied to the runtime stage without
# running apt-get update/upgrade in the runtime image.
RUN apt-get update && \
    apt-get install -y --no-install-recommends libjemalloc2 && \
    rm -rf /var/lib/apt/lists/* && \
    # Copy to a fixed path so the runtime stage can COPY it without shell variable expansion
    cp /usr/lib/$(uname -m)-linux-gnu/libjemalloc.so.2 /tmp/libjemalloc.so.2

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
# dist/package.json is a minimal manifest listing only the 2 externalized packages
# (log4js, swagger-ui-express); npm install uses it to avoid installing all production deps.
COPY --from=builder --chown=node:node /home/node/app/dist ./dist
RUN npm config set update-notifier false && \
    cp dist/package.json package.json && \
    npm install --omit=dev && \
    find node_modules -type f \( \
        -name '*.ts' ! -name '*.d.ts' -o \
        -name '*.js.map' -o \
        -name '*.d.ts' -o \
        -name '*.d.ts.map' -o \
        -name '*.tsbuildinfo' \
    \) -delete && \
    find node_modules -type d -name tests -exec rm -rf {} + 2>/dev/null || true && \
    find node_modules -type d -name __tests__ -exec rm -rf {} + 2>/dev/null || true && \
    npm cache clean --force --loglevel=error && \
    chown -R node:node .
# openapiHandler.js resolves api/v1/openapi.yml via process.cwd(), so api/ must be at the app root
COPY --from=builder --chown=node:node /home/node/app/api ./api
# Copy libjemalloc from the builder stage and configure it as a preloaded library.
# The builder copies it to /tmp/libjemalloc.so.2 (fixed path) so COPY works here without
# shell variable expansion, then a RUN moves it to the arch-specific destination.
COPY --from=builder /tmp/libjemalloc.so.2 /tmp/libjemalloc.so.2
RUN cp /tmp/libjemalloc.so.2 /usr/lib/$(uname -m)-linux-gnu/libjemalloc.so.2 && \
    echo /usr/lib/$(uname -m)-linux-gnu/libjemalloc.so.2 >> /etc/ld.so.preload && \
    rm /tmp/libjemalloc.so.2

USER node

# No extensionless/register needed — esbuild resolves extensionless imports at build time
ENTRYPOINT ["node", "dist/server.js"]