// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PublishPropertiesTest {

    private PublishScenarioProperties publishScenarioProperties;
    private PublishProperties publishProperties;

    @BeforeEach
    void setup() {
        publishScenarioProperties = new PublishScenarioProperties();
        publishProperties = new PublishProperties();
        publishProperties.getScenarios().put("test1", publishScenarioProperties);
    }

    @Test
    void validate() {
        publishProperties.validate();
        assertThat(publishScenarioProperties.getName()).isEqualTo("test1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void emptyName(String name) {
        publishProperties.getScenarios().put(name, publishScenarioProperties);
        assertThrows(IllegalArgumentException.class, publishProperties::validate);
    }

    @Test
    void nullName() {
        publishProperties.getScenarios().put(null, publishScenarioProperties);
        assertThrows(IllegalArgumentException.class, publishProperties::validate);
    }

    @Test
    void noScenarios() {
        publishProperties.getScenarios().clear();
        assertThrows(IllegalArgumentException.class, publishProperties::validate);
    }

    @Test
    void noScenariosDisabled() {
        publishProperties.setEnabled(false);
        publishProperties.getScenarios().clear();
        publishProperties.validate();
    }
}
