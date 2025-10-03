#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

source ./utils/utils.sh
source ./utils/input-utils.sh
source ./utils/snapshot-utils.sh

REPLACE_DISKS="${REPLACE_DISKS:-true}"

configureAndValidateSnapshotRestore
replaceDisks
