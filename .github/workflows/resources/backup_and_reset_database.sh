#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

NAMESPACE=$1
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLEANUP_SQL="${SCRIPT_DIR}/../../../importer/src/main/resources/db/scripts/cleanup.sql"

if [[ ! -f "${CLEANUP_SQL}" ]]; then
  echo "Error: cleanup.sql not found at ${CLEANUP_SQL}" >&2
  exit 1
fi

POD_NAME=$(kubectl -n "${NAMESPACE}" get pods -l app.kubernetes.io/name=postgres,app.kubernetes.io/component=primary -o jsonpath='{.items[0].metadata.name}')

if [[ -z "${POD_NAME}" ]]; then
  echo "Error: no primary postgres pod found in namespace ${NAMESPACE}" >&2
  exit 1
fi

echo "Will backup tables to mirror_node_wrb and cleanup tables in mirror_node on postgresql pod ${POD_NAME}"
kubectl cp "${CLEANUP_SQL}" "${NAMESPACE}/${POD_NAME}:/tmp/cleanup.sql"

cat <<'EOF' | kubectl exec -i -n "${NAMESPACE}" "${POD_NAME}" -- bash
set -euo pipefail
export PGPASSWORD=$(cat /opt/bitnami/postgresql/secrets/password)
export PGUSER=postgres

psql -c 'create database mirror_node_wrb;'
pg_dump -d mirror_node --schema-only | psql -d mirror_node_wrb > /dev/null
pg_dump -d mirror_node -a \
  -t contract -t contract_action -t contract_state_change \
  -t record_file -t sidecar_file -t transaction \
  | psql -d mirror_node_wrb
psql -d mirror_node -f /tmp/cleanup.sql
EOF
