// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.contract.ContractState;
import org.hiero.mirror.common.domain.contract.ContractStateChange;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.state.ContractSlotValue;
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

    @Test
    void findStorageBatchSingleValueSuccessfulCall() {
        ContractState contractState = domainBuilder.contractState().persist();
        final var slots = new byte[1][];
        slots[0] = contractState.getSlot();
        assertThat(contractStateRepository.findStorageBatch(contractState.getContractId(), slots))
                .isEqualTo(List.of(new ContractSlotValue(contractState.getSlot(), contractState.getValue())));
    }

    @Test
    void findStorageBatchMultipleValuesSuccessfulCall() {
        final var contractId = domainBuilder.id();
        final var contractSlotsCount = 10;
        final var contractSlots = new byte[contractSlotsCount][];
        final var contractSlotValuesList = new LinkedList<ContractSlotValue>();
        for (int i = 0; i < contractSlotsCount; i++) {
            final var contractState = domainBuilder
                    .contractState()
                    .customize(cs -> cs.contractId(contractId))
                    .persist();
            contractSlots[i] = contractState.getSlot();
            contractSlotValuesList.add(new ContractSlotValue(contractState.getSlot(), contractState.getValue()));
        }
        assertThat(contractStateRepository.findStorageBatch(contractId, contractSlots))
                .isEqualTo(contractSlotValuesList);
    }

    @Test
    void findStorageBatchNotPersistedKeyDoesNotReturnValue() {
        final var contractId = domainBuilder.id();
        final var slots = new byte[1][];
        slots[0] = domainBuilder.bytes(32);
        assertThat(contractStateRepository.findStorageBatch(contractId, slots)).isEmpty();
    }

    @Test
    void findStorageBatchDuplicateKeysDoesNotReturnDuplicateValues() {
        final var contractId = domainBuilder.id();
        final var contractSlotsCount = 2;
        final var contractSlots = new byte[contractSlotsCount + 1][];
        final var contractSlotValuesList = new LinkedList<ContractSlotValue>();
        for (int i = 0; i < contractSlotsCount; i++) {
            final var contractState = domainBuilder
                    .contractState()
                    .customize(cs -> cs.contractId(contractId))
                    .persist();
            contractSlots[i] = contractState.getSlot();
            contractSlotValuesList.add(new ContractSlotValue(contractState.getSlot(), contractState.getValue()));
        }
        // Duplicate one for the keys
        contractSlots[contractSlotsCount] = contractSlots[0];

        assertThat(contractStateRepository.findStorageBatch(contractId, contractSlots))
                .containsAll(contractSlotValuesList);
    }
}
