FROM gcr.io/cloud-marketplace-tools/k8s/deployer_helm

# Marketplace image still depends upon Helm 3
# Use helm 4.3.0-rc.1 temporarility to address vulnerability
# Use kubectl v1.37.0 to address the same vulnerability until the next marketplace deploy_helm image release
ENV HELM_VERSION=4.3.0-rc.1
ENV KUBECTL_VERSION=v1.37.0
ENV YQ_VERSION=v4.53.2

# Install yq
RUN wget https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/yq_linux_amd64 && \
    mv yq_linux_amd64 /usr/local/bin/yq && \
    chmod +x /usr/local/bin/yq

# Install Helm
RUN wget -q https://get.helm.sh/helm-v${HELM_VERSION}-linux-amd64.tar.gz -O - | \
    tar -C /usr/local/bin --strip-components=1 -zxv linux-amd64/helm

# Install kubectl
RUN rm -rf /opt/kubectl && \
    mkdir -p /opt/kubectl/default && \
    wget -q -O /opt/kubectl/default/kubectl https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl && \
    chmod 755 /opt/kubectl/default/kubectl

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
