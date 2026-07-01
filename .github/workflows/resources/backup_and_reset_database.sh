#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

NAMESPACE=$1

POD_NAME=$(kubectl -n "${NAMESPACE}" get pods -l app.kubernetes.io/name=postgres,app.kubernetes.io/component=primary -o jsonpath='{.items[0].metadata.name}')

echo "Will backup tables to mirror_node_wrb and cleanup tables in mirror_node on postgresql pod ${POD_NAME}"
cat <<'EOF' | kubectl exec -i -n "${NAMESPACE}" "${POD_NAME}" -- bash
export PGPASSWORD=$(cat /opt/bitnami/postgresql/secrets/password)
export PGUSER=postgres

psql -c 'create database mirror_node_wrb;'
pg_dump -d mirror_node --schema-only | psql -d mirror_node_wrb
pg_dump -d mirror_node -a \
  -t contract -t contract_action -t contract_state_change \
  -t record_file -t sidecar_file -t transaction \
  | psql -d mirror_node_wrb
curl https://raw.githubusercontent.com/hiero-ledger/hiero-mirror-node/refs/heads/main/importer/src/main/resources/db/scripts/cleanup.sql | \
  psql -d mirror_node
EOF
