FROM node:26.13.1-trixie-slim@sha256:abe7e5a82b61a8ef67b315b85497c9e65173387ccf2f4a029248184b8e020be5
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
