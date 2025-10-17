// SPDX-License-Identifier: Apache-2.0

description = "Mirror Node Rosetta API"

plugins {
    id("docker-conventions")
    id("go-conventions")
}

go {
    pkg = "./app/..."
    version = "1.25.2"
}
