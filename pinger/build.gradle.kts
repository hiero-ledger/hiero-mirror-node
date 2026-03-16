// SPDX-License-Identifier: Apache-2.0

description = "Hiero Mirror Pinger"

plugins {
    id("docker-conventions")
    id("go-conventions")
}

go {
    pkg = "./..."
    version = "1.26.0"
}
