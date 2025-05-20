// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.contract.ContractState;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ContractStateRepositoryTest extends Web3IntegrationTest {

    private final ContractStateRepository contractStateRepository;

    @Test
    void findStorageSuccessfulCall() {
        ContractState contractState = domainBuilder.contractState().persist();
        assertThat(contractStateRepository.findStorage(contractState.getContractId(), contractState.getSlot()))
                .get()
                .isEqualTo(contractState.getValue());
    }

    @Test
    void findStorageOfContractStateChangeByBlockTimestampSuccessfulCall() {
        ContractStateChange olderContractState =
                domainBuilder.contractStateChange().persist();
        ContractStateChange contractStateChange = domainBuilder
                .contractStateChange()
                .customize(
                        cs -> cs.contractId(olderContractState.getContractId()).slot(olderContractState.getSlot()))
                .persist();

        assertThat(contractStateRepository.findStorageByBlockTimestamp(
                        contractStateChange.getContractId(),
                        contractStateChange.getSlot(),
                        contractStateChange.getConsensusTimestamp()))
                .get()
                .isEqualTo(contractStateChange.getValueWritten());
    }

    @Test
    void findStorageOfContractStateChangeWithEmptyValueWrittenByBlockTimestampSuccessfulCall() {
        ContractStateChange olderContractState =
                domainBuilder.contractStateChange().persist();
        ContractStateChange contractStateChange = domainBuilder
                .contractStateChange()
                .customize(cs -> cs.contractId(olderContractState.getContractId())
                        .slot(olderContractState.getSlot())
                        .valueWritten(null))
                .persist();

        assertThat(contractStateRepository.findStorageByBlockTimestamp(
                        contractStateChange.getContractId(),
                        contractStateChange.getSlot(),
                        contractStateChange.getConsensusTimestamp()))
                .get()
                .isEqualTo(contractStateChange.getValueRead());
    }

    @Test
    void findStorageOfContractStateChangeByBlockTimestampFailCall() {
        ContractStateChange contractStateChange =
                domainBuilder.contractStateChange().persist();

        assertThat(contractStateRepository.findStorageByBlockTimestamp(
                        contractStateChange.getContractId(),
                        contractStateChange.getSlot(),
                        contractStateChange.getConsensusTimestamp() - 1))
                .isEmpty();
    }

    @Test
    void findStorageFailCall() {
        ContractState contractState = domainBuilder.contractState().persist();
        long id = contractState.getContractId();
        assertThat(contractStateRepository.findStorage(++id, contractState.getSlot()))
                .isEmpty();
    }

    @Test
    void findStorageDifferentSlotCall() {
        ContractState contractState = domainBuilder.contractState().persist();
        assertThat(contractStateRepository.findStorage(contractState.getContractId(), new byte[20]))
                .isEmpty();
    }
}
