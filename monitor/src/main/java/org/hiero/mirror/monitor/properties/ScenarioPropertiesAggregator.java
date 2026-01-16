// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.properties;

import java.util.Map;

public interface ScenarioPropertiesAggregator {
    Map<String, Object> aggregateProperties(Map<String, String> properties);
}
