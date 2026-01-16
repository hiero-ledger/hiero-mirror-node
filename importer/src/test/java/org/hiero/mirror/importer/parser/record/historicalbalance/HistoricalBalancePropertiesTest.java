// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.historicalbalance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class HistoricalBalancePropertiesTest extends ImporterIntegrationTest {

    private final HistoricalBalanceProperties properties;

    @Test
    void conflictConfig() {
        properties.getBalanceDownloaderProperties().setEnabled(true);
        assertThatThrownBy(properties::init).isInstanceOf(IllegalArgumentException.class);
        properties.getBalanceDownloaderProperties().setEnabled(false);
    }
}
