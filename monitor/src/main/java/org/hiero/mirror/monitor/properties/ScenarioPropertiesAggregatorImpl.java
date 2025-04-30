// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.properties;

import com.google.common.collect.Lists;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.CustomLog;

@CustomLog
@Named
public class ScenarioPropertiesAggregatorImpl implements ScenarioPropertiesAggregator {

    private static final Pattern LIST_PATTERN_END = Pattern.compile("(\\w+)\\.\\d+");

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> aggregateProperties(Map<String, String> properties) {
        // List properties are loaded in as property.0, property.1, etc.  This puts them into a list for
        // deserialization.
        Map<String, Object> correctedProperties = new HashMap<>();

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Matcher matcher = LIST_PATTERN_END.matcher(entry.getKey());
            if (matcher.matches()) {
                String propertyName = matcher.group(1);
                log.debug("Converting property {} into list {}", entry.getKey(), propertyName);
                correctedProperties.merge(propertyName, Lists.newArrayList(entry.getValue()), (e, n) -> {
                    if (e instanceof List<?> existingList && n instanceof Collection<?> newList) {
                        ((List<Object>) existingList).addAll(newList);
                    } else {
                        log.warn("Unable to merge {} to {}", n, e);
                    }
                    return e;
                });
            } else {
                correctedProperties.put(entry.getKey(), entry.getValue());
            }
        }

        return correctedProperties;
    }
}
