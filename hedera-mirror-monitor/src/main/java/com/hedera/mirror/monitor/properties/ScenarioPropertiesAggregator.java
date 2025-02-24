// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.properties;

import java.util.Map;

public interface ScenarioPropertiesAggregator {
    Map<String, Object> aggregateProperties(Map<String, String> properties);
}
