FROM paketobuildpacks/ubuntu-noble-build:latest

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       libsodium23 \
    && arch="$(dpkg-architecture -qDEB_HOST_MULTIARCH)" \
    && ln -sf "/usr/lib/${arch}/libsodium.so.23" "/usr/lib/${arch}/libsodium.so" \
    && mkdir -p /workspace \
    && cp -f "/usr/lib/${arch}/libsodium.so" /workspace/libsodium.so \
    && rm -rf /var/lib/apt/lists/*

USER cnb