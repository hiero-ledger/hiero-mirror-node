FROM gcr.io/cloud-marketplace-tools/k8s/deployer_helm:13.0.17@sha256:e2f38bc77f3ab3ce8da9d7e2befe793210c8bf2ea4e8a8ce1a5cd3d55db3d008

# Marketplace image still depends upon Helm 3
# Use kubectl v1.37.2 to address the same vulnerability until the next marketplace deploy_helm image release
ARG HELM_SHA256=0403a88ae20913d11b10a2467b50087721fea7581736da6b9d3992f5357fe54b
ARG HELM_VERSION=4.3.1
ARG KUBECTL_SHA256=c04487523e7aa7d1493f7808174c899db93d7a9cf57531420cd76b9bd415f8f4
ARG KUBECTL_VERSION=v1.37.2
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
