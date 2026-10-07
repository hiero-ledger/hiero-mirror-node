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

        assertThat(contractStateChangeRepository.findModifiedByConsensusTimestamp(timestamp, -1L, new byte[0], 10))
                .containsExactly(modified);
    }

    @Test
    void findModifiedByConsensusTimestampIncludesNullValueWrittenWhenValueReadIsPresent() {
        final var timestamp = domainBuilder.timestamp();
        final var modified = persistStateChange(timestamp, 1L, new byte[] {1}, domainBuilder.bytes(64), null);

        assertThat(contractStateChangeRepository.findModifiedByConsensusTimestamp(timestamp, -1L, new byte[0], 10))
                .containsExactly(modified);
    }

    @Test
    void findByConsensusTimestampPagesByContractIdAndSlot() {
        final var timestamp = domainBuilder.timestamp();
        final var first =
                persistStateChange(timestamp, 1L, new byte[] {1}, domainBuilder.bytes(64), domainBuilder.bytes(64));
        final var second =
                persistStateChange(timestamp, 1L, new byte[] {2}, domainBuilder.bytes(64), domainBuilder.bytes(64));
        final var third =
                persistStateChange(timestamp, 2L, new byte[] {1}, domainBuilder.bytes(64), domainBuilder.bytes(64));

        assertThat(contractStateChangeRepository.findByConsensusTimestamp(timestamp, -1L, new byte[0], 2))
                .containsExactly(first, second);
        assertThat(contractStateChangeRepository.findByConsensusTimestamp(
                        timestamp, second.getContractId(), second.getSlot(), 2))
                .containsExactly(third);
        assertThat(contractStateChangeRepository.findByConsensusTimestamp(
                        timestamp, third.getContractId(), third.getSlot(), 10))
                .isEmpty();
    }

    @Test
    void findModifiedByConsensusTimestampPagesByContractIdAndSlot() {
        final var timestamp = domainBuilder.timestamp();
        final var unchangedValue = domainBuilder.bytes(64);
        final var first =
                persistStateChange(timestamp, 1L, new byte[] {1}, domainBuilder.bytes(64), domainBuilder.bytes(64));
        persistStateChange(timestamp, 1L, new byte[] {2}, unchangedValue, unchangedValue);
        final var third =
                persistStateChange(timestamp, 2L, new byte[] {1}, domainBuilder.bytes(64), domainBuilder.bytes(64));

        assertThat(contractStateChangeRepository.findModifiedByConsensusTimestamp(timestamp, -1L, new byte[0], 1))
                .containsExactly(first);
        assertThat(contractStateChangeRepository.findModifiedByConsensusTimestamp(
                        timestamp, first.getContractId(), first.getSlot(), 1))
                .containsExactly(third);
        assertThat(contractStateChangeRepository.findModifiedByConsensusTimestamp(
                        timestamp, third.getContractId(), third.getSlot(), 10))
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
