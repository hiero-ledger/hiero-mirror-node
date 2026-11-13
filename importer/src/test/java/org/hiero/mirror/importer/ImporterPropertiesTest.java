// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hiero.mirror.importer.ImporterProperties.HederaNetwork;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

final class ImporterPropertiesTest {

    @ParameterizedTest
    @ValueSource(strings = {"mainnet", "MAINNET", "MainNet", "testnet", "previewnet", "demo", "other", "integration"})
    void verifyNetwork(String networkName) {
        var properties = new ImporterProperties();
        properties.setNetwork(networkName);
        assertThat(properties.getNetwork()).isEqualTo(networkName.toLowerCase());
    }

    @CsvSource(textBlock = """
            DEMO, false
            MAINNET,false
            OTHER,true
            PREVIEWNET,true
            TESTNET,true
            """)
    @ParameterizedTest
    void isResettable(String networkName, boolean expected) {
        assertThat(HederaNetwork.isResettable(networkName)).isEqualTo(expected);
    }
}
