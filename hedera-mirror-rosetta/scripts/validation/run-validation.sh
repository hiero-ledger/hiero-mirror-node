#!/usr/bin/env bash

# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

ROSETTA_CLI_VERSION=${ROSETTA_CLI_VERSION:-}

parent_path="$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )"
cd "${parent_path}"
network=${1:-demo}
api=${2:-data}

if [[ "$network" != "demo" && "$network" != "mainnet" && "$network" != "previewnet" && "$network" != "testnet" ]]; then
    echo "Unsupported network ${network}"
    exit 1
fi

check="check:$api"
config="./${network}/validation.json"

if (./rosetta-cli 2>&1 | grep 'CLI for the Rosetta API' > /dev/null); then
    echo "Rosetta CLI already installed. Skipping installation"
else
    echo "Installing Rosetta CLI"
    # rosetta-cli repo has been renamed to mesh-cli, however the install script didn't change the repo accordingly
    curl -sSfL https://raw.githubusercontent.com/coinbase/mesh-cli/master/scripts/install.sh | \
      sed -e 's/^REPO=.*/REPO=mesh-cli/' | \
      sh -s -- -b . "${ROSETTA_CLI_VERSION}"
fi

echo "Running Rosetta ${api} API Validation against ${network} Network"

if (! ./rosetta-cli "${check}" --configuration-file="${config}"); then
    echo "Failed to Pass ${api} API Validation"
    exit 1
fi

echo "Rosetta ${api} Validation Passed Successfully!"
