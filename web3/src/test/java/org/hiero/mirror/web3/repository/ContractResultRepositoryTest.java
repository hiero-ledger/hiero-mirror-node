// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractResultRepositoryTest extends Web3IntegrationTest {

    private final ContractResultRepository contractResultRepository;

    @Test
    void findByConsensusTimestampSuccessful() {
        var contractResult = domainBuilder.contractResult().persist();
        assertThat(contractResultRepository.findById(contractResult.getConsensusTimestamp()))
                .contains(contractResult);
    }

    @Test
    void findLatestExecutedTimestampReturnsOnlyThoseWithFunctionResult() {
        // A genuine execution has a non-empty function_result.
        final var executed = domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(1L))
                .persist();
        // A pre-execution failure result has no function_result (null and empty must both be excluded).
        final var nullResult = domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(2L).functionResult(null))
                .persist();
        final var emptyResult = domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(3L).functionResult(new byte[0]))
                .persist();

        final var contractIds =
                List.of(executed.getContractId(), nullResult.getContractId(), emptyResult.getContractId());
        assertThat(contractResultRepository.findLatestExecutedTimestamp(List.of(1L, 2L, 3L), contractIds))
                .contains(executed.getConsensusTimestamp());
    }

    @Test
    void findLatestExecutedTimestampReturnsLatestWhenSeveralExecuted() {
        // When several candidates executed, the latest by consensus timestamp wins.
        final var earlier = domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(1L))
                .persist();
        final var later = domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(2L))
                .persist();

        assertThat(contractResultRepository.findLatestExecutedTimestamp(
                        List.of(1L, 2L), List.of(earlier.getContractId(), later.getContractId())))
                .contains(later.getConsensusTimestamp());
    }

    @Test
    void findLatestExecutedTimestampPrunesByContractId() {
        // A genuine execution excluded because its contract_id is not among the candidates (citus shard pruning).
        final var executed = domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(1L))
                .persist();

        assertThat(contractResultRepository.findLatestExecutedTimestamp(
                        List.of(1L), List.of(executed.getContractId() + 1)))
                .isEmpty();
    }

    @Test
    void findLatestExecutedTimestampReturnsEmptyWhenNoneExecuted() {
        final var nullResult = domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(1L).functionResult(null))
                .persist();

        assertThat(contractResultRepository.findLatestExecutedTimestamp(
                        List.of(1L, 2L), List.of(nullResult.getContractId())))
                .isEmpty();
    }
}
