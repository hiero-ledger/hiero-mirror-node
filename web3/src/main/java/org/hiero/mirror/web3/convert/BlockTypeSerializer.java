// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.convert;

import org.hiero.mirror.web3.viewmodel.BlockType;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

public class BlockTypeSerializer extends StdSerializer<BlockType> {

    public BlockTypeSerializer() {
        super(BlockType.class);
    }

    @Override
    public void serialize(BlockType value, JsonGenerator gen, SerializationContext context) {
        gen.writeString(value.name());
    }
}
