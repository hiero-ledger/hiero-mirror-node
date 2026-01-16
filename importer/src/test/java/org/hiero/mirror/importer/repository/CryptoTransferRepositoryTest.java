// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class CryptoTransferRepositoryTest extends ImporterIntegrationTest {

    private final CryptoTransferRepository cryptoTransferRepository;

    @Test
    void prune() {
        domainBuilder.cryptoTransfer().persist();
        var cryptoTransfer2 = domainBuilder.cryptoTransfer().persist();
        var cryptoTransfer3 = domainBuilder.cryptoTransfer().persist();

        cryptoTransferRepository.prune(cryptoTransfer2.getConsensusTimestamp());

        assertThat(cryptoTransferRepository.findAll()).containsExactly(cryptoTransfer3);
    }
}
