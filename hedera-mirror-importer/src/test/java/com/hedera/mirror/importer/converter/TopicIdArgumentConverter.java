// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.converter;

import com.hederahashgraph.api.proto.java.TopicID;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.ArgumentConverter;

public final class TopicIdArgumentConverter implements ArgumentConverter {
    @Override
    public Object convert(Object input, ParameterContext parameterContext) throws ArgumentConversionException {
        if (null == input) {
            return null;
        }
        if (!(input instanceof String)) {
            throw new ArgumentConversionException(input + " is not a string");
        }
        var parts = ((String) input).split("\\.");
        return TopicID.newBuilder()
                .setShardNum(Long.parseLong(parts[0]))
                .setRealmNum(Long.parseLong(parts[1]))
                .setTopicNum(Long.parseLong(parts[2]))
                .build();
    }
}
