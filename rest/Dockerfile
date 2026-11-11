FROM node:24.22.3-trixie-slim@sha256:0a085df29353bca2c119e8fa2171773a4c7eab2a13330ee88b5e91ee4b21de9e AS builder
WORKDIR /dist

RUN apt-get update && \
    apt-get install -y --no-install-recommends libjemalloc2 && \
    cp /usr/lib/$(uname -m)-linux-gnu/libjemalloc.so.2 /usr/lib/libjemalloc.so.2 && \
    echo /usr/lib/libjemalloc.so.2 >> /etc/ld.so.preload

COPY package*.json ./
RUN npm config set update-notifier false && \
    npm ci

COPY . ./
RUN npm run build && \
    mv node_modules buildDist/node_modules && \
    npm prune --omit=dev --prefix buildDist && \
    find buildDist/node_modules \( \
        -type f \( -name '*.ts' ! -name '*.d.ts' -o -name '*.js.map' -o -name '*.d.ts' -o -name '*.d.ts.map' -o -name '*.tsbuildinfo' \) -o \
        -type d \( -name tests -o -name __tests__ \) \
    \) -exec rm -rf {} + 2>/dev/null || true

FROM node:24.22.3-trixie-slim@sha256:0a085df29353bca2c119e8fa2171773a4c7eab2a13330ee88b5e91ee4b21de9e
LABEL maintainer="mirrornode@hedera.com"

ENV NODE_ENV=production
EXPOSE 5551
HEALTHCHECK --interval=10s --retries=3 --start-period=25s --timeout=2s \
  CMD node -e "fetch('http://localhost:5551/health/liveness').then(r=>r.ok?process.exit(0):process.exit(1)).catch(()=>process.exit(1))"
WORKDIR /home/node/app/

COPY --from=builder --chown=node:node /dist/buildDist ./
COPY --from=builder /usr/lib/libjemalloc.so.2 /usr/lib/libjemalloc.so.2
COPY --from=builder /etc/ld.so.preload /etc/ld.so.preload

# Temporarily install latest OS updates until next Node.js. Remove the bundled npm since it's not needed at runtime and
# its vendored dependencies (e.g. undici) get flagged by vulnerability scanners.
RUN apt-get update && \
    apt-get upgrade -y --no-install-recommends && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/* && \
    rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx

USER node

ENTRYPOINT ["node", "server.js"]