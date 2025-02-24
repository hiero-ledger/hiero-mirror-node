// SPDX-License-Identifier: Apache-2.0

description = "Hedera Mirror Node Rosetta API"

plugins {
    id("docker-conventions")
    id("go-conventions")
}

go {
    pkg = "./app/..."
    version = "1.23.3"
}
