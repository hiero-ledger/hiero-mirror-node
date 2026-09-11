FROM gcr.io/cloud-marketplace-tools/k8s/deployer_helm:13.0.10@sha256:60bc54af5578550df364109be383ea575ea97fdcae4c8236398f8647a5f75bee

# Marketplace image still depends upon Helm 3
# Use kubectl v1.37.0 to address the same vulnerability until the next marketplace deploy_helm image release
ARG HELM_SHA256=86584a54def73570558f66f5111cc53dfed56689637ae32c1201205d494f54fb
ARG HELM_VERSION=4.3.0
ARG KUBECTL_SHA256=6129359f4e1f3848a5572ccb0b26cf28b8ca08cef38c95a765b2f64a2c961a2f
ARG KUBECTL_VERSION=v1.37.0
ARG YQ_SHA256=c5f056448f973ae7d39b5401949648a78f2dc1947d6a8eb65be60d5c504b9385
ARG YQ_VERSION=v4.53.6

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
