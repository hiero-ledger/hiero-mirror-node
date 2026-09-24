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
    void findExecutedTimestampsReturnsOnlyThoseWithFunctionResult() {
        // A genuine execution has a non-empty function_result.
        final var executed = domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(1L))
                .persist();
        // A pre-execution failure result has no function_result (null and empty must both be excluded).
        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(2L).functionResult(null))
                .persist();
        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(3L).functionResult(new byte[0]))
                .persist();

        assertThat(contractResultRepository.findExecutedTimestamps(List.of(1L, 2L, 3L)))
                .containsExactly(executed.getConsensusTimestamp());
    }

    @Test
    void findExecutedTimestampsReturnsEmptyWhenNoneExecuted() {
        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(1L).functionResult(null))
                .persist();

        assertThat(contractResultRepository.findExecutedTimestamps(List.of(1L, 2L)))
                .isEmpty();
    }
}
