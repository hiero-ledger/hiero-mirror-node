#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

source ./utils.sh
source ./snapshot-utils.sh

REPLACE_DISKS="${REPLACE_DISKS:-true}"

configureAndValidateSnapshotRestore
replaceDisks
