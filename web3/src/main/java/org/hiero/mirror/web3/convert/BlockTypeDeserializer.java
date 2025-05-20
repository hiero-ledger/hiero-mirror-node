// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.convert;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import org.hiero.mirror.web3.viewmodel.BlockType;

public class BlockTypeDeserializer extends JsonDeserializer<BlockType> {

    @Override
    public BlockType deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException {
        var value = jsonParser.readValueAs(String.class);
        return BlockType.of(value);
    }
}
