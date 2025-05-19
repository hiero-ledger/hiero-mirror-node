// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.converter;

import com.hederahashgraph.api.proto.java.TopicID;
import org.hiero.mirror.common.CommonProperties;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.ArgumentConverter;

public final class TopicIdArgumentConverter implements ArgumentConverter {

    @Override
    public Object convert(Object input, ParameterContext parameterContext) throws ArgumentConversionException {
        if (null == input) {
            return null;
        }

        if (input instanceof String inputStr) {
            return TopicID.newBuilder()
                    .setShardNum(CommonProperties.getInstance().getShard())
                    .setRealmNum(CommonProperties.getInstance().getRealm())
                    .setTopicNum(Long.parseLong(inputStr))
                    .build();
        }

        throw new ArgumentConversionException(input + " is not a string");
    }
}
