// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractStateChangeRepositoryTest extends Web3IntegrationTest {

    private final ContractStateChangeRepository contractStateChangeRepository;

    @Test
    void findModifiedByConsensusTimestampReturnsOnlyChangedValues() {
        final var timestamp = domainBuilder.timestamp();
        final var unchangedValue = domainBuilder.bytes(64);
        final var modified =
                persistStateChange(timestamp, 1L, new byte[] {1}, domainBuilder.bytes(64), domainBuilder.bytes(64));
        persistStateChange(timestamp, 2L, new byte[] {1}, unchangedValue, unchangedValue);
        persistStateChange(timestamp + 1, 1L, new byte[] {2}, domainBuilder.bytes(64), domainBuilder.bytes(64));

        assertThat(contractStateChangeRepository.findModifiedByConsensusTimestamp(timestamp, 10, 0))
                .containsExactly(modified);
    }

    @Test
    void findModifiedByConsensusTimestampIncludesNullValueWrittenWhenValueReadIsPresent() {
        final var timestamp = domainBuilder.timestamp();
        final var modified = persistStateChange(timestamp, 1L, new byte[] {1}, domainBuilder.bytes(64), null);

        assertThat(contractStateChangeRepository.findModifiedByConsensusTimestamp(timestamp, 10, 0))
                .containsExactly(modified);
    }

    @Test
    void findModifiedByConsensusTimestampAppliesLimitAndOffset() {
        final var timestamp = domainBuilder.timestamp();
        final var first =
                persistStateChange(timestamp, 1L, new byte[] {1}, domainBuilder.bytes(64), domainBuilder.bytes(64));
        final var second =
                persistStateChange(timestamp, 1L, new byte[] {2}, domainBuilder.bytes(64), domainBuilder.bytes(64));
        final var third =
                persistStateChange(timestamp, 2L, new byte[] {1}, domainBuilder.bytes(64), domainBuilder.bytes(64));

        assertThat(contractStateChangeRepository.findModifiedByConsensusTimestamp(timestamp, 2, 0))
                .containsExactly(first, second);
        assertThat(contractStateChangeRepository.findModifiedByConsensusTimestamp(timestamp, 1, 2))
                .containsExactly(third);
        assertThat(contractStateChangeRepository.findModifiedByConsensusTimestamp(timestamp, 10, 10))
                .isEmpty();
    }

    private ContractStateChange persistStateChange(
            final long consensusTimestamp,
            final long contractId,
            final byte[] slot,
            final byte[] valueRead,
            final byte[] valueWritten) {
        return domainBuilder
                .contractStateChange()
                .customize(c -> c.consensusTimestamp(consensusTimestamp)
                        .contractId(contractId)
                        .slot(slot)
                        .valueRead(valueRead)
                        .valueWritten(valueWritten))
                .persist();
    }
}
