// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.reader.block;

import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.importer.reader.StreamFileReader;

public interface BlockFileReader extends StreamFileReader<BlockFile, BlockItem> {}
