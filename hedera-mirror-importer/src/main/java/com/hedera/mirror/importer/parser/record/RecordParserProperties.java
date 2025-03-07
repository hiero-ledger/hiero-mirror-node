// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.parser.AbstractParserProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component("recordParserProperties")
@Data
@Validated
@ConfigurationProperties("hedera.mirror.importer.parser.record")
public class RecordParserProperties extends AbstractParserProperties {

    @Override
    public StreamType getStreamType() {
        return StreamType.RECORD;
    }
}
