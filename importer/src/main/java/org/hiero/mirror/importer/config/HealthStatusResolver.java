// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import org.springframework.boot.health.contributor.Status;

@FunctionalInterface
interface HealthStatusResolver {
    Status resolve(String group);
}
