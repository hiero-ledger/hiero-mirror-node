// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.REVERT_REASON;
import static com.hedera.services.stream.proto.ContractActionType.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractActionRepositoryTest extends Web3IntegrationTest {
    private final ContractActionRepository contractActionRepository;

    @Test
    void findAllByConsensusTimestampSuccessful() {
        final var timestamp = domainBuilder.timestamp();
        final var otherActions = List.of(
                domainBuilder
                        .contractAction()
                        .customize(action -> action.consensusTimestamp(timestamp))
                        .persist(),
                domainBuilder
                        .contractAction()
                        .customize(action -> action.consensusTimestamp(timestamp))
                        .persist());
        final var failedSystemActions = List.of(
                domainBuilder
                        .contractAction()
                        .customize(action -> action.callType(SYSTEM.getNumber())
                                .consensusTimestamp(timestamp)
                                .resultDataType(REVERT_REASON.getNumber()))
                        .persist(),
                domainBuilder
                        .contractAction()
                        .customize(action -> action.callType(SYSTEM.getNumber())
                                .consensusTimestamp(timestamp)
                                .resultDataType(REVERT_REASON.getNumber()))
                        .persist());

        assertThat(contractActionRepository.findFailedSystemActionsByConsensusTimestamp(timestamp))
                .containsExactlyElementsOf(failedSystemActions)
                .doesNotContainAnyElementsOf(otherActions);
    }
}
