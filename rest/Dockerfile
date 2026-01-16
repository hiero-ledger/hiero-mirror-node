FROM node:24.13.0-trixie-slim
LABEL maintainer="mirrornode@hedera.com"

# Setup
ENV DEBIAN_FRONTEND=noninteractive
ENV NODE_ENV=production
EXPOSE 5551
HEALTHCHECK --interval=10s --retries=3 --start-period=25s --timeout=2s CMD wget -q -O- http://localhost:5551/health/liveness
WORKDIR /home/node/app/

# Install dependencies
COPY package*.json ./
RUN npm config set update-notifier false && \
    npm ci --omit=dev && \
    npm cache clean --force --loglevel=error && \
    chown -R node:node .
COPY --chown=node:node . ./

# Install OS updates, install wget, and use jemalloc
RUN apt-get update && \
    apt-get upgrade -y --no-install-recommends && \
    apt-get install -y libjemalloc2 wget && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/* && \
    echo /usr/lib/$(uname -m)-linux-gnu/libjemalloc.so.2 >> /etc/ld.so.preload
USER node

# Run
ENTRYPOINT ["node", "--import=extensionless/register", "server.js"]
