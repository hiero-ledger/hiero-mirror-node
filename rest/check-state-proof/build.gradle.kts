// SPDX-License-Identifier: Apache-2.0

description = "Mirror Node Check State Proof"

plugins { id("javascript-conventions") }

// This project imports code from the parent project
tasks.npmInstall { dependsOn(":rest:npmInstall") }
