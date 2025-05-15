// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import org.hiero.mirror.importer.parser.AbstractParserProperties;
import org.hiero.mirror.importer.parser.record.RecordParserProperties;

class RecordStreamFileHealthIndicatorTest extends AbstractStreamFileHealthIndicatorTest {
    @Override
    public AbstractParserProperties getParserProperties() {
        return new RecordParserProperties();
    }
}
