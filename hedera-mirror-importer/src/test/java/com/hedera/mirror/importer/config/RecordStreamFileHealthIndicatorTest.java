// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.config;

import com.hedera.mirror.importer.parser.AbstractParserProperties;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;

class RecordStreamFileHealthIndicatorTest extends AbstractStreamFileHealthIndicatorTest {
    @Override
    public AbstractParserProperties getParserProperties() {
        return new RecordParserProperties();
    }
}
