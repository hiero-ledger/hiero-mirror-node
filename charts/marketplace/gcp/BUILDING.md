# Overview

This folder contains logic to support the development and deployment of the Hedera Mirror Node in a Kubernetes cluster
in [Google Cloud Platform Marketplace](https://console.cloud.google.com/marketplace). It takes in the wrapper
`hedera-mirror` Helm chart and bundles it into the required deployer image which is built and pushed to Google Container
Registry on deploy.

# Setup

A Google Kubernetes Engine (GKE) cluster is required to verify and test the deployment of the Marketplace application.
Other clusters like minikube will not work. Follow the
GKE [quickstart](https://cloud.google.com/kubernetes-engine/docs/quickstart) to create a test cluster. Once created,
manually install the `Application` custom resource definition (CRD) into the cluster.

```shell
kubectl apply -f "https://raw.githubusercontent.com/GoogleCloudPlatform/marketplace-k8s-app-tools/master/crd/app-crd.yaml"
```

## Prerequisites

- [docker](https://docs.docker.com/install/)
- [gcloud](https://cloud.google.com/sdk/gcloud/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/)
- [mpdev](https://github.com/GoogleCloudPlatform/marketplace-k8s-app-tools/blob/master/docs/mpdev-references.md)

## Set environment variables

First ensure the following environment variables are populated with the appropriate version and names. These variables
will be used for the remainder of the document.

```shell
export NAME="marketplace"
export NAMESPACE="marketplace"
export TAG="x.y.z"
```

# Building

Apps are required to supply a `deployer` container image which is used in UI-based deployment. The image should extend
from one of the base images provided in the marketplace-k8s-app-tools registry. This solution currently
extends `gcr.io/cloud-marketplace-tools/k8s/deployer_helm`. Our deployer image build logic is specified in
the [Dockerfile](Dockerfile).

Additionally, GCP Marketplace restricts applications to images pulled from the Marketplace registry. Since the Mirror
Node uses some third party images like PostgreSQL, we need to re-publish these images to our staging registry and keep
them up to date. The image must be published as an OCI image for the required marketplace annotations to be attached.
To republish postgresql-repmgr, run the following steps manually whenever it needs to change:

```shell
export PG_VERSION=17.6.0-debian-12-r2
git clone https://github.com/bitnami/containers.git
cd containers/bitnami/postgresql-repmgr/17/debian-12
docker buildx build . -t gcr.io/mirror-node-public/hedera-mirror-node/postgresql-repmgr:${PG_VERSION} --push --provenance false --platform linux/amd64 --annotation "manifest,manifest-descriptor:com.googleapis.cloudmarketplace.product.service.name=services/hedera-mirror-node-mirror-node-public.cloudpartnerservices.goog"
docker manifest inspect gcr.io/mirror-node-public/hedera-mirror-node/postgresql-repmgr:${PG_VERSION} | grep "vnd.oci.image"
sed "s/postgresql_tag=.*/postgresql_tag=\"${PG_VERSION}\"/" release.sh
```

Run the below commands to re-tag existing images and build the deployer image for testing with Marketplace:

```shell
cd charts/marketplace/gcp
./release.sh "${TAG}"
```

# Testing

The mpdev tool is provided by Google Cloud Platform Marketplace to help verify the marketplace solution and install it.
If you are trying to run mpdev on a mac M1 arm64 machine, it will not work. Google has not provided an arm64 image.
You will need to create a non-arm64 VM to install mpdev and then run verify.

## Verify

Run `mpdev verify` to automatically install the application in a new namespace, execute acceptance tests against it and
uninstall it. Ensure [schema-test.yaml](schema-test.yaml) contains defaults for any required fields
in [schema.yaml](schema.yaml).

```shell
mpdev verify --deployer=gcr.io/mirror-node-public/hedera-mirror-node/deployer:${TAG}
```

## Install

To install run `mpdev install`. Note the properties in the `required` section of the [schema.yaml](schema.yaml)
file must be specified via the parameters flag.

```shell
kubectl create namespace "${NAMESPACE}"
mpdev install --deployer=gcr.io/mirror-node-public/hedera-mirror-node/deployer:${TAG} --parameters='{"name": "'${NAME}'",
    "namespace": "'${NAMESPACE}'",
    "importer.config.hiero.mirror.importer.downloader.accessKey": "GOOG1...",
    "importer.config.hiero.mirror.importer.downloader.secretKey": "...",
    "importer.config.hiero.mirror.importer.network": "MAINNET"}'
```

## Uninstall

Once installed in a Kubernetes cluster the Marketplace solution can be cleaned up with the following commands:

```shell
kubectl delete application "${NAME}" -n "${NAMESPACE}"
kubectl delete -n "${NAMESPACE}" $(kubectl get pvc -n "${NAMESPACE}" -o name)
```

Or you can simply delete the entire namespace if you created it during installation, and it's no longer needed:

```shell
kubectl delete namespace "${NAMESPACE}"
```

# Releasing

Once all local testing is completed successfully and the images are tagged, create the version on the Marketplace
Producer Portal and submit it.
