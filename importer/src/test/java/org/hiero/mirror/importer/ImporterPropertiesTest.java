// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ImporterPropertiesTest {

    @ParameterizedTest
    @ValueSource(strings = {"mainnet", "MAINNET", "MainNet", "testnet", "previewnet", "demo", "other", "integration"})
    void verifyNetwork(String networkName) {
        var properties = new ImporterProperties();
        properties.setNetwork(networkName);
        assertThat(properties.getNetwork()).isEqualTo(networkName.toLowerCase());
    }
}
