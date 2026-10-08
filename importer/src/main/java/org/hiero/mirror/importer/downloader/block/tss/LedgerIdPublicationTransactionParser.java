// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import com.hedera.hapi.node.tss.legacy.LedgerIdPublicationTransactionBody;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.tss.Ledger;
import org.hiero.mirror.common.util.DomainUtils;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
@RequiredArgsConstructor
public final class LedgerIdPublicationTransactionParser {

    public Ledger parse(final long consensusTimestamp, final LedgerIdPublicationTransactionBody transactionBody) {
        return Ledger.builder()
                .consensusTimestamp(consensusTimestamp)
                .ledgerId(DomainUtils.toBytes(transactionBody.getLedgerId()))
                .build();
    }
}
