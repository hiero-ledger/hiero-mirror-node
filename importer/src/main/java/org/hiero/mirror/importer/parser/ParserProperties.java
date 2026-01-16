// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser;

import java.time.Duration;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.importer.parser.AbstractParserProperties.BatchProperties;

public interface ParserProperties {

    BatchProperties getBatch();

    Duration getFrequency();

    Duration getProcessingTimeout();

    StreamType getStreamType();

    boolean isEnabled();

    void setEnabled(boolean enabled);
}
