#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -ex

if [[ "$#" -lt 1 ]]; then
    echo "You must provide the Mirror Node image tag (e.g. 0.116.0)"
    exit 1
fi

# Same source and target tags
if [[ "$#" -eq 1 ]]; then
    source_tag="${1}"
    target_tag="${source_tag}"
fi

# Different source and target tags. Useful for testing chart changes with existing tags.
if [[ "$#" -eq 2 ]]; then
    source_tag="${1}"
    target_tag="${2}"
fi

annotation="com.googleapis.cloudmarketplace.product.service.name=services/hedera-mirror-node-mirror-node-public.cloudpartnerservices.goog"
bats_tag="1.11.1"
postgresql_tag="17.6.0-debian-12-r2"
registry="gcr.io/mirror-node-public/hedera-mirror-node"
target_tag="${target_tag#v}" # Strip v prefix if present
target_tag_minor="${target_tag%\.*}"

function retag() {
    local source=${1}
    local image=${2}
    local target="${registry}/${image}"

    # Main image
    if [[ -z "${image}" ]]; then
        target="${registry}"
    fi

    retag_single "${source}" "${target}:${target_tag}"

    # Don't update minor tag for pre-release tags
    if [[ ! "${target_tag}" =~ .*-.* ]]; then
        retag_single "${source}" "${target}:${target_tag_minor}"
    fi
}

function retag_single() {
    local source=${1}
    local target=${2}
    digest=$(docker buildx imagetools inspect "${source}" --raw | jq '.manifests[] | select(.platform.architecture=="amd64" and .platform.os=="linux") | .digest' -r)
    source_image="${source//\:*/}"
    docker buildx imagetools create "${source_image}@${digest}" --tag "${target}" --annotation "index,manifest-descriptor:${annotation}"
}

# Ensure chart app version matches schema.yaml version
sed "-i.bak" "s/version: .*/version: ${target_tag}/" values.yaml

# Build Marketplace deployer image
docker buildx build --push -f ./Dockerfile --provenance false -t "${registry}/deployer:${target_tag}" -t "${registry}/deployer:${target_tag_minor}" --platform linux/amd64 --build-arg TAG="${target_tag}" --annotation "manifest,manifest-descriptor:${annotation}" ../..

# Re-tag our manually built postgresql-repmgr image
docker buildx imagetools create "${registry}/postgresql-repmgr:${postgresql_tag}" --tag "${registry}/postgresql-repmgr:${target_tag}" --tag "${registry}/postgresql-repmgr:${target_tag_minor}" --annotation "index,manifest-descriptor:${annotation}"

# Re-tag other images
retag "bats/bats:${bats_tag}" "test"
retag "gcr.io/mirrornode/hedera-mirror-grpc:${source_tag}" "grpc"
retag "gcr.io/mirrornode/hedera-mirror-importer:${source_tag}" ""
retag "gcr.io/mirrornode/hedera-mirror-rest:${source_tag}" "rest"

echo "Successfully pushed all images"
exit 0
