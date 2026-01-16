// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record;

import lombok.Data;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.importer.parser.AbstractParserProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component("recordParserProperties")
@Data
@Validated
@ConfigurationProperties("hiero.mirror.importer.parser.record")
public class RecordParserProperties extends AbstractParserProperties {

    @Override
    public StreamType getStreamType() {
        return StreamType.RECORD;
    }
}
