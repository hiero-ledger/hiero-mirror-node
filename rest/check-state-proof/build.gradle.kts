// SPDX-License-Identifier: Apache-2.0

description = "Mirror Node Check State Proof"

plugins { id("javascript-conventions") }

node { version = "20.15.1" }

// This project imports code from the parent project
tasks.npmInstall { dependsOn(":rest:npmInstall") }
