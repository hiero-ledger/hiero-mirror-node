// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import com.hedera.hapi.block.stream.protoc.BlockItem;
import java.util.List;

public record BlockStream(List<BlockItem> blockItems, byte[] bytes, String filename, long loadStart, long nodeId) {}
