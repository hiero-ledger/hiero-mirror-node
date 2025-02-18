# This Dockerfile configuration is used to build
# Importer, Rosetta and PostgreSQL into one image
# and run the services using supervisord

FROM ubuntu:noble AS builder
ENV LANG=C.UTF-8
ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update -y --no-install-recommends && apt-get install -y gcc git openjdk-21-jdk-headless wget
# GIT_REF can be a branch, a tag, or a 40-charater git commit hash
ARG GIT_REF=main
ARG REPO_URL=https://github.com/hiero-ledger/hiero-mirror-node
RUN git clone --depth 1 -n "${REPO_URL}" hedera-mirror-node \
    && cd hedera-mirror-node \
    && git fetch --depth 1 origin "${GIT_REF}" \
    && git reset --hard FETCH_HEAD
WORKDIR /hedera-mirror-node
RUN ./gradlew :importer:build :rosetta:build -x test
WORKDIR /app
RUN mkdir importer rosetta scripts \
    && cp -r /hedera-mirror-node/hedera-mirror-rosetta/container/* . \
    && rm /hedera-mirror-node/hedera-mirror-importer/build/libs/importer-*-plain.jar \
    && cp /hedera-mirror-node/hedera-mirror-importer/build/libs/importer-*.jar ./importer/hedera-mirror-importer.jar \
    && cp /hedera-mirror-node/hedera-mirror-rosetta/build/hedera-mirror-rosetta ./rosetta \
    && cp /hedera-mirror-node/hedera-mirror-importer/src/main/resources/db/scripts/init.sh ./scripts \
    && cp /hedera-mirror-node/hedera-mirror-importer/src/main/resources/db/scripts/init-temp-schema.sh ./scripts

# ######################################################################## #
# --------------------------- Runner Container --------------------------- #
# ######################################################################## #

FROM ubuntu:noble AS runner

# ---------------------- Install Deps & PostgreSQL ------------------------ #
# Add the PostgreSQL PGP key to verify their Debian packages.
# It should be the same key as https://www.postgresql.org/media/keys/ACCC4CF8.asc
ARG DEBIAN_FRONTEND=noninteractive
ENV PG_CLUSTER_NAME=rosetta
ENV PG_VERSION=14
RUN apt-get update \
    && apt-get install -y ca-certificates curl gnupg lsb-release \
    && apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys B97B0AFCAA1A47F044F244A07FCC7D46ACCC4CF8 \
    && echo "deb https://apt.postgresql.org/pub/repos/apt/ $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list \
    && apt-get update -y --no-install-recommends \
    && apt-get install -y postgresql-${PG_VERSION} postgresql-client-${PG_VERSION} supervisor openjdk-21-jre-headless \
    && rm -rf /var/lib/apt/lists/*

# Create users for importer and rosetta
RUN useradd -s /bin/bash importer && useradd -s /bin/bash rosetta

ENV PATH="/usr/lib/postgresql/${PG_VERSION}/bin:${PATH}"
ENV PGDATA="/data/pgdata"
ENV PGCONF=${PGDATA}
RUN rm -fr /etc/postgresql/${PG_VERSION}/main
VOLUME /data

# Set stats_temp_directory to the default, i.e., "pg_stat_tmp" relative to the postgresql.conf file
RUN sed -i 's/^stats_temp_directory/#stats_temp_directory/g' /etc/postgresql-common/createcluster.conf

USER root
WORKDIR /app

# Expose the ports (DB)(Rosetta)
EXPOSE 5432 5700
COPY --from=builder /app /app
ENTRYPOINT [ "./entrypoint.sh" ]
