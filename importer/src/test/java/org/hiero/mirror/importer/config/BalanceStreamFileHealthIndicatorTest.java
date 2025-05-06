// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import org.hiero.mirror.importer.parser.AbstractParserProperties;
import org.hiero.mirror.importer.parser.balance.BalanceParserProperties;

class BalanceStreamFileHealthIndicatorTest extends AbstractStreamFileHealthIndicatorTest {
    @Override
    public AbstractParserProperties getParserProperties() {
        return new BalanceParserProperties();
    }
}
