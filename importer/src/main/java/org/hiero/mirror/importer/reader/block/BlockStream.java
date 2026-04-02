// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import com.hedera.hapi.block.stream.protoc.BlockItem;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record BlockStream(List<BlockItem> blockItems, byte @Nullable [] bytes, String filename, long loadStart) {}
