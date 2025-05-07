// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.block;

import com.hedera.mirror.common.domain.transaction.BlockFile;

public interface BlockStreamReader {

    BlockFile read(BlockStream blockStream);
}
