#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -e

echo "Setting user and role for Mirror Node"
PGPASSWORD=$DB_PASS psql -v ON_ERROR_STOP=1 --username "$DB_USER" --dbname "$DB_NAME" -p "$DB_PORT" -h localhost <<-EOSQL
    \set db_user 'mirror_node'
    \set db_password 'mirror_node_pass'

    create user :db_user with login createrole password :'db_password';

EOSQL

echo "Importing Mirror Node Data"
echo "Connecting to Postgres $DB_NAME database at localhost:$DB_PORT under user $DB_USER"
PGPASSWORD=$DB_PASS pg_restore -c -v -U "${DB_USER}" -d "${DB_NAME}" -p "${DB_PORT}" -h localhost /tmp/pgdump.gz
echo "Restored Mirror Node Data from backup"
