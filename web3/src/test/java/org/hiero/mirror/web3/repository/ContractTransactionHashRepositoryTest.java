// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractTransactionHash;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.repository.projections.ContractTransactionHashLookup;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractTransactionHashRepositoryTest extends Web3IntegrationTest {
    private final ContractTransactionHashRepository contractTransactionHashRepository;

    @Test
    void findAllByHashReturnsMatch() {
        var hash = domainBuilder.contractTransactionHash().persist();
        assertThat(contractTransactionHashRepository.findAllByHash(hash.getHash()))
                .extracting(
                        ContractTransactionHashLookup::getConsensusTimestamp,
                        ContractTransactionHashLookup::getEntityId,
                        ContractTransactionHashLookup::getPayerAccountId,
                        ContractTransactionHashLookup::getTransactionResult)
                .containsExactly(tuple(
                        hash.getConsensusTimestamp(),
                        hash.getEntityId(),
                        hash.getPayerAccountId(),
                        hash.getTransactionResult()));
    }

    @Test
    void findAllByHashOrdersSuccessFirstThenLatest() {
        final var hash = domainBuilder.bytes(32);
        // An earlier due-diligence failure (e.g. INSUFFICIENT_PAYER_BALANCE) records first at T1.
        final var earlierFailure = persist(hash, ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_VALUE);
        // The genuine execution succeeds later at T2 > T1, sharing the same keccak hash.
        final var successful = persist(hash, ResponseCodeEnum.SUCCESS_VALUE);
        // A later duplicate failure at T3 > T2.
        final var laterFailure = persist(hash, ResponseCodeEnum.DUPLICATE_TRANSACTION_VALUE);

        // Success first, then the remaining rows latest-first by consensus timestamp.
        assertThat(contractTransactionHashRepository.findAllByHash(hash))
                .extracting(ContractTransactionHashLookup::getConsensusTimestamp)
                .containsExactly(
                        successful.getConsensusTimestamp(),
                        laterFailure.getConsensusTimestamp(),
                        earlierFailure.getConsensusTimestamp());
    }

    @Test
    void findAllByHashOrdersLatestFirstWhenNoSuccessExists() {
        final var hash = domainBuilder.bytes(32);
        final var earlier = persist(hash, ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_VALUE);
        final var latest = persist(hash, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED_VALUE);

        assertThat(contractTransactionHashRepository.findAllByHash(hash))
                .extracting(ContractTransactionHashLookup::getConsensusTimestamp)
                .containsExactly(latest.getConsensusTimestamp(), earlier.getConsensusTimestamp());
    }

    @Test
    void findAllByHashReturnsEmptyWhenAbsent() {
        assertThat(contractTransactionHashRepository.findAllByHash(domainBuilder.bytes(32)))
                .isEmpty();
    }

    private ContractTransactionHash persist(final byte[] hash, final int transactionResult) {
        return domainBuilder
                .contractTransactionHash()
                .customize(c -> c.hash(hash)
                        .consensusTimestamp(domainBuilder.timestamp())
                        .transactionResult(transactionResult))
                .persist();
    }
}
