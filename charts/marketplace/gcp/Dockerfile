FROM gcr.io/cloud-marketplace-tools/k8s/deployer_helm:13.0.18@sha256:8c885a105f59461d6d1e001b8a2f230c159fcd9ff26f0935fadfa603b2807f37

# Marketplace image still depends upon Helm 3
ARG HELM_SHA256=d1c0278d46beab79582d7e50f5b3dc206944ba8e577cde618513fd07daa53983
ARG HELM_VERSION=4.3.2
ARG KUBECTL_SHA256=9a1b55af2795b25dd288dbef62387db9b5530087ab918d28c6ad067f2e3cb07c
ARG KUBECTL_VERSION=v1.37.3
ARG YQ_SHA256=129534601871538efe0ba4c5e62a34fb1d5f469bcb975c4fddeb467d82974973
ARG YQ_VERSION=v4.54.3

# Install yq
RUN set -eu && \
    wget -q -O /usr/local/bin/yq https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/yq_linux_amd64 && \
    chmod 755 /usr/local/bin/yq && \
    echo "${YQ_SHA256} /usr/local/bin/yq" | sha256sum -c -

# Install Helm
RUN set -eu && \
    wget -q -O helm.tar.gz https://get.helm.sh/helm-v${HELM_VERSION}-linux-amd64.tar.gz && \
    echo "${HELM_SHA256} helm.tar.gz" | sha256sum -c - && \
    tar -C /usr/local/bin --strip-components=1 -zxf helm.tar.gz linux-amd64/helm && \
    rm -f helm.tar.gz

# Install kubectl
RUN set -eu && \
    rm -rf /opt/kubectl && \
    mkdir -p /opt/kubectl/default && \
    wget -q -O /opt/kubectl/default/kubectl https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl && \
    chmod 755 /opt/kubectl/default/kubectl && \
    echo "${KUBECTL_SHA256} /opt/kubectl/default/kubectl" | sha256sum -c -

# Pull in charts
WORKDIR /app
COPY . ./

# Merge values files
RUN yq -i '. *= load("marketplace/gcp/values.yaml")' hedera-mirror/values.yaml && rm /usr/local/bin/yq

ARG TAG
RUN sed -i "s/\$TAG/$TAG/g" marketplace/gcp/schema.yaml

# Run helm template to render and verify templates
RUN helm dependency update hedera-mirror
RUN helm template hedera-mirror -f hedera-mirror/values.yaml

# Build chart archive, copy hedera-mirror contents to chart dir to comply with mpdev expectations
RUN mv hedera-mirror chart && tar -czvf hedera-mirror-node.tar.gz chart
RUN mv /app/hedera-mirror-node.tar.gz /data/chart
RUN mv /app/marketplace/gcp/schema-test.yaml /data-test/schema.yaml
RUN mv /app/marketplace/gcp/schema.yaml /data
