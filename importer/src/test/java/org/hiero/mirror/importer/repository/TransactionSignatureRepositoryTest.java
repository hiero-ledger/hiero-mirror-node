// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.TransactionSignature;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TransactionSignatureRepositoryTest extends ImporterIntegrationTest {

    private final TransactionSignatureRepository transactionSignatureRepository;

    @Test
    void prune() {
        domainBuilder.transactionSignature().persist();
        var transactionSignature2 = domainBuilder.transactionSignature().persist();
        var transactionSignature3 = domainBuilder.transactionSignature().persist();

        transactionSignatureRepository.prune(transactionSignature2.getConsensusTimestamp());

        assertThat(transactionSignatureRepository.findAll()).containsExactly(transactionSignature3);
    }

    @Test
    void save() {
        TransactionSignature transactionSignature =
                domainBuilder.transactionSignature().persist();
        assertThat(transactionSignatureRepository.findById(transactionSignature.getId()))
                .get()
                .isEqualTo(transactionSignature);
    }
}
