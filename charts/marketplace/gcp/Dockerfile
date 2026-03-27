FROM gcr.io/cloud-marketplace-tools/k8s/deployer_helm

ENV HELM_VERSION=v4.1.3
ENV KUBECTL_VERSION=v1.32.13
ENV YQ_VERSION=v4.52.4

# Install yq
RUN wget https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/yq_linux_amd64 && \
    mv yq_linux_amd64 /usr/local/bin/yq && \
    chmod +x /usr/local/bin/yq

# Install kubectl
RUN rm -rf /opt/kubectl && \
    mkdir -p /opt/kubectl/default && \
    wget -q -O /opt/kubectl/default/kubectl https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl && \
    chmod 755 /opt/kubectl/default/kubectl

# Install Helm
RUN wget -q https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz -O - | \
    tar -C /usr/bin --strip-components=1 -zxv linux-amd64/helm

# Pull in charts
WORKDIR /app
COPY . ./

# Merge values files
RUN yq -i '. *= load("marketplace/gcp/values.yaml")' hedera-mirror/values.yaml && rm /usr/local/bin/yq

ARG TAG
RUN sed -i "s/\$TAG/$TAG/g" marketplace/gcp/schema.yaml

# Update dependencies every time since base image is not well maintained
RUN apt-get update && apt-get upgrade -y && rm -rf /var/lib/apt/lists/* && pip3 install "pyOpenSSL>=26.0.0"

# Run helm template to render and verify templates
RUN helm dependency update hedera-mirror
RUN helm template hedera-mirror -f hedera-mirror/values.yaml

# Build chart archive, copy hedera-mirror contents to chart dir to comply with mpdev expectations
RUN mv hedera-mirror chart && tar -czvf hedera-mirror-node.tar.gz chart
RUN mv /app/hedera-mirror-node.tar.gz /data/chart
RUN mv /app/marketplace/gcp/schema-test.yaml /data-test/schema.yaml
RUN mv /app/marketplace/gcp/schema.yaml /data
