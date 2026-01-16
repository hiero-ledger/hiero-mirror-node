// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.convert;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import org.hiero.mirror.web3.viewmodel.BlockType;

public class BlockTypeSerializer extends JsonSerializer<BlockType> {

    @Override
    public void serialize(BlockType value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value != null) {
            gen.writeString(value.name());
        } else {
            gen.writeNull();
        }
    }
}
