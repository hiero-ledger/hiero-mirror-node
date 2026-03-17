FROM paketobuildpacks/ubuntu-noble-build:latest

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       libsodium23 \
    && ln -sf /usr/lib/aarch64-linux-gnu/libsodium.so.23 /usr/lib/aarch64-linux-gnu/libsodium.so \
    && mkdir -p /workspace \
    && cp -f /usr/lib/aarch64-linux-gnu/libsodium.so /workspace/libsodium.so \
    && rm -rf /var/lib/apt/lists/*

ENV LD_LIBRARY_PATH=/usr/lib/aarch64-linux-gnu

USER cnb