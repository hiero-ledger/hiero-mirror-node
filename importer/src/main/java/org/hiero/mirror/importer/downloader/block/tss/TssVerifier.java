// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import org.hiero.mirror.common.domain.tss.Ledger;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface TssVerifier {

    void setLedger(Ledger ledger);

    void verify(final long blockNumber, byte[] message, byte[] signature);
}
