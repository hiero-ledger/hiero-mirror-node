// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.historicalbalance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
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
