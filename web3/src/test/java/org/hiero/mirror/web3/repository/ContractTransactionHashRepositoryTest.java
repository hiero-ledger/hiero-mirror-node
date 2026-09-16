// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractTransactionHashRepositoryTest extends Web3IntegrationTest {
    private final ContractTransactionHashRepository contractTransactionHashRepository;

    @Test
    void findByHashSuccessful() {
        var contractTransactionHash = domainBuilder.contractTransactionHash().persist();
        assertThat(contractTransactionHashRepository.findByHash(contractTransactionHash.getHash()))
                .contains(contractTransactionHash);
    }

    @Test
    void findByHashPrefersSuccessfulOverEarlierFailedAttempt() {
        final var hash = domainBuilder.bytes(32);
        // An earlier due-diligence failure (e.g. INSUFFICIENT_PAYER_BALANCE) records first at T1.
        domainBuilder
                .contractTransactionHash()
                .customize(c -> c.hash(hash)
                        .consensusTimestamp(domainBuilder.timestamp())
                        .transactionResult(ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_VALUE))
                .persist();
        // The genuine execution succeeds later at T2 > T1, sharing the same keccak hash.
        final var successful = domainBuilder
                .contractTransactionHash()
                .customize(c -> c.hash(hash)
                        .consensusTimestamp(domainBuilder.timestamp())
                        .transactionResult(ResponseCodeEnum.SUCCESS_VALUE))
                .persist();

        assertThat(contractTransactionHashRepository.findByHash(hash)).contains(successful);
    }

    @Test
    void findByHashPrefersSuccessfulOverLaterFailedAttempt() {
        final var hash = domainBuilder.bytes(32);
        // The genuine execution succeeds and consumes the nonce.
        final var successful = domainBuilder
                .contractTransactionHash()
                .customize(c -> c.hash(hash)
                        .consensusTimestamp(domainBuilder.timestamp())
                        .transactionResult(ResponseCodeEnum.SUCCESS_VALUE))
                .persist();
        domainBuilder
                .contractTransactionHash()
                .customize(c -> c.hash(hash)
                        .consensusTimestamp(domainBuilder.timestamp())
                        .transactionResult(ResponseCodeEnum.TRANSACTION_EXPIRED_VALUE))
                .persist();

        assertThat(contractTransactionHashRepository.findByHash(hash)).contains(successful);
    }

    @Test
    void findByHashPrefersRealExecutionOverLaterFailedStub() {
        final var hash = domainBuilder.bytes(32);
        final long contractId = domainBuilder.entityId().getId();
        // Transaction reverted while executing against a contract at T1, so it resolves to a non-zero entity id.
        final var executed = domainBuilder
                .contractTransactionHash()
                .customize(c -> c.hash(hash)
                        .consensusTimestamp(domainBuilder.timestamp())
                        .entityId(contractId)
                        .transactionResult(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED_VALUE))
                .persist();
        // Fails pre-execution, so only a default/stub hash row with entity 0 is produced later at T2 > T1.
        domainBuilder
                .contractTransactionHash()
                .customize(c -> c.hash(hash)
                        .consensusTimestamp(domainBuilder.timestamp())
                        .entityId(0L)
                        .transactionResult(ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_VALUE))
                .persist();

        assertThat(contractTransactionHashRepository.findByHash(hash)).contains(executed);
    }

    @Test
    void findByHashReturnsLatestNonSuccessWhenNoSuccessExists() {
        final var hash = domainBuilder.bytes(32);
        // With no SUCCESS row the ordering falls back to consensus_timestamp desc and returns the latest row
        domainBuilder
                .contractTransactionHash()
                .customize(c -> c.hash(hash)
                        .consensusTimestamp(domainBuilder.timestamp())
                        .transactionResult(ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_VALUE))
                .persist();
        final var latest = domainBuilder
                .contractTransactionHash()
                .customize(c -> c.hash(hash)
                        .consensusTimestamp(domainBuilder.timestamp())
                        .transactionResult(ResponseCodeEnum.CONTRACT_REVERT_EXECUTED_VALUE))
                .persist();

        assertThat(contractTransactionHashRepository.findByHash(hash)).contains(latest);
    }
}
