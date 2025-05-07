// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.record.sidecar;

import com.hedera.mirror.common.domain.transaction.SidecarFile;
import org.hiero.mirror.importer.domain.StreamFileData;

public interface SidecarFileReader {

    void read(SidecarFile sidecarFile, StreamFileData streamFileData);
}
