FROM node:24.22.3-trixie-slim@sha256:0a085df29353bca2c119e8fa2171773a4c7eab2a13330ee88b5e91ee4b21de9e
LABEL maintainer="mirrornode@hedera.com"

# Setup
ENV DEBIAN_FRONTEND=noninteractive
ENV NODE_ENV=production
EXPOSE 3000
HEALTHCHECK --interval=10s --retries=3 --start-period=25s --timeout=2s CMD wget -q -O- http://localhost:3000/health/liveness
WORKDIR /home/node/app/

# Install dependencies
COPY package*.json ./
RUN npm config set update-notifier false && \
    npm ci --omit=dev && \
    npm cache clean --force --loglevel=error && \
    chown -R node:node .
COPY --chown=node:node . ./

# Install OS updates
RUN apt-get update && \
    apt-get upgrade -y --no-install-recommends && \
    apt-get install -y wget && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/*
USER node

# Run
ENTRYPOINT ["node", "--import=extensionless/register", "server.js"]
