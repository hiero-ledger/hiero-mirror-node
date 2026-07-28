// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ImporterPropertiesTest {

    @ParameterizedTest(name = "Network {1} is canonical network {0}")
    @CsvSource({
        "testnet, testnet",
        "testnet, testnet-",
        "testnet, teSTnet-2023-01",
        "testnet, testnet-someprefix",
        "mainnet, mainnet",
        "mainnet, mainnet-2023-01",
        "mainnet, maiNNet-someprefix",
        "previewnet, previewnet",
        "previewnet, Previewnet-2025-04",
        "previewnet, previewnet-abcdef",
        "demo, deMo",
        "demo, demo-2023-01",
        "demo, demo-someprefix",
        "other, other",
        "other, other-2050-02",
        "other, othER-world"
    })
    void verifyCanonicalNetwork(String expectedHederaNetwork, String networkName) {

        var properties = new ImporterProperties();
        properties.setNetwork(networkName);
        assertThat(properties.getNetwork()).isEqualTo(expectedHederaNetwork);
    }

    @ParameterizedTest(name = "Network {1} is non-canonical network {0}")
    @CsvSource({"integration, integration", "integration, integration-2023-01", "dev, dev", "dev, dev-2025-02"})
    void verifyNonCanonicalNetwork(String expectedNetwork, String networkName) {

        var properties = new ImporterProperties();
        properties.setNetwork(networkName);
        assertThat(properties.getNetwork()).isEqualTo(expectedNetwork);
    }
}
