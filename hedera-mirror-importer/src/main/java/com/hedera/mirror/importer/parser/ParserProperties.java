// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.parser.AbstractParserProperties.BatchProperties;
import java.time.Duration;

public interface ParserProperties {

    BatchProperties getBatch();

    Duration getFrequency();

    Duration getProcessingTimeout();

    StreamType getStreamType();

    boolean isEnabled();

    void setEnabled(boolean enabled);
}
