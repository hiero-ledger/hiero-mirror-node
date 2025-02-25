// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.config;

import com.hedera.mirror.importer.parser.AbstractParserProperties;
import com.hedera.mirror.importer.parser.balance.BalanceParserProperties;

class BalanceStreamFileHealthIndicatorTest extends AbstractStreamFileHealthIndicatorTest {
    @Override
    public AbstractParserProperties getParserProperties() {
        return new BalanceParserProperties();
    }
}
