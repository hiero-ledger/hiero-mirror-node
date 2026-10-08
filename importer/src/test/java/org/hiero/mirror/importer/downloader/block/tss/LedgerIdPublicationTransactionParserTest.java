// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.toBytes;

import org.hiero.mirror.common.domain.RecordItemBuilder;
import org.hiero.mirror.common.domain.tss.Ledger;
import org.junit.jupiter.api.Test;

final class LedgerIdPublicationTransactionParserTest {

    @Test
    void parse() {
        // given
        var recordItemBuilder = new RecordItemBuilder();
        var recordItem = recordItemBuilder.ledgerIdPublication().build();
        var body = recordItem.getTransactionBody().getLedgerIdPublication();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var parser = new LedgerIdPublicationTransactionParser();

        // when
        var ledger = parser.parse(consensusTimestamp, body);

        // then
        assertThat(ledger)
                .returns(recordItem.getConsensusTimestamp(), Ledger::getConsensusTimestamp)
                .returns(toBytes(body.getLedgerId()), Ledger::getLedgerId);
    }
}
