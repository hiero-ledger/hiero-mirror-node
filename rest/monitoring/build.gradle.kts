// SPDX-License-Identifier: Apache-2.0

description = "Mirror Node Monitor API"

plugins {
    id("docker-conventions")
    id("javascript-conventions")
}

project.extra.set("dockerImageName", "hedera-mirror-rest-monitor")
