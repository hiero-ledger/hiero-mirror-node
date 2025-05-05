// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ScenarioPropertiesAggregatorImplTest {

    private final ScenarioPropertiesAggregator scenarioPropertiesAggregator = new ScenarioPropertiesAggregatorImpl();

    @SuppressWarnings("unchecked")
    @Test
    void propertiesWithLists() {
        Map<String, String> properties = new HashMap<>();
        properties.put("senderAccountId", "0.0.2");
        properties.put("transferTypes.0", "CRYPTO");
        properties.put("transferTypes.1", "TOKEN");
        properties.put("otherProperty.0", "TEST");
        properties.put("otherProperty.1", "TEST2");

        Map<String, Object> correctedProperties = scenarioPropertiesAggregator.aggregateProperties(properties);
        assertThat(correctedProperties.keySet())
                .containsExactlyInAnyOrder("senderAccountId", "transferTypes", "otherProperty");

        assertThat(correctedProperties).containsEntry("senderAccountId", "0.0.2");
        assertThat(correctedProperties.get("transferTypes")).isInstanceOf(List.class);
        assertThat((List) correctedProperties.get("transferTypes")).containsExactlyInAnyOrder("CRYPTO", "TOKEN");
        assertThat(correctedProperties.get("otherProperty")).isInstanceOf(List.class);
        assertThat((List) correctedProperties.get("otherProperty")).containsExactlyInAnyOrder("TEST", "TEST2");
    }
}
