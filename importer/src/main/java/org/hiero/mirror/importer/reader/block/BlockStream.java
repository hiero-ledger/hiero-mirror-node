// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import java.util.List;
import org.hiero.block.internal.protoc.BlockItemUnparsed;
import org.jspecify.annotations.Nullable;

/**
 * A block read from a block file or streamed from a block node. The block items are unparsed, i.e., each item is kept as
 * its serialized bytes, so the block root hash can be calculated from the block items as produced, and only the items
 * needed are parsed.
 */
public record BlockStream(
        List<BlockItemUnparsed> blockItems,
        long blockCompleteTime,
        byte @Nullable [] bytes,
        String filename,
        long loadStart,
        int size) {}
