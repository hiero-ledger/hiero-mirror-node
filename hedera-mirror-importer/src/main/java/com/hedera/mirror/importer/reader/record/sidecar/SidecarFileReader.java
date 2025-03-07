// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.reader.record.sidecar;

import com.hedera.mirror.common.domain.transaction.SidecarFile;
import com.hedera.mirror.importer.domain.StreamFileData;

public interface SidecarFileReader {

    void read(SidecarFile sidecarFile, StreamFileData streamFileData);
}
