// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.expression;

import java.util.LinkedHashMap;
import java.util.Map;

public interface ExpressionConverter {

    default Map<String, String> convert(Map<String, String> properties) {
        Map<String, String> converted = new LinkedHashMap<>();
        properties.forEach((key, value) -> converted.put(key, convert(value)));
        return converted;
    }

    String convert(String property);
}
