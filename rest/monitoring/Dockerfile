FROM node:24.22.3-trixie-slim@sha256:af02d57b57719b9657a45199a79264a9437940cd55b3c7b3fc99615654f124a0
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
