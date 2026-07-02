// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
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
        assertThat(contractStateRepository.findStorageBatch(
                        contractState.getContractId(), new byte[][] {contractState.getSlot()}))
                .isEqualTo(List.of(new ContractSlotValue(contractState.getSlot(), contractState.getValue())));
    }

    @Test
    void findStorageBatchMultipleValuesSuccessfulCall() {
        final var contractId = domainBuilder.id();
        final var contractSlotsCount = 10;
        final var contractSlotsList = new LinkedList<byte[]>();
        final var contractSlotValuesList = new LinkedList<ContractSlotValue>();
        for (int i = 0; i < contractSlotsCount; i++) {
            final var contractState = domainBuilder
                    .contractState()
                    .customize(cs -> cs.contractId(contractId))
                    .persist();
            contractSlotsList.add(contractState.getSlot());
            contractSlotValuesList.add(new ContractSlotValue(contractState.getSlot(), contractState.getValue()));
        }
        assertThat(contractStateRepository.findStorageBatch(contractId, contractSlotsList.toArray(byte[][]::new)))
                .isEqualTo(contractSlotValuesList);
    }

    @Test
    void findStorageBatchNotPersistedKeyDoesNotReturnValue() {
        final var contractId = domainBuilder.id();
        assertThat(contractStateRepository.findStorageBatch(
                        contractId,
                        new byte[][] {domainBuilder.contractState().get().getSlot()}))
                .isEmpty();
    }

    @Test
    void findInitialStorageSlotsReturnsLowIndexSlots() {
        final var contractId = domainBuilder.id();
        final var slot0 = toPaddedSlotKey(0);
        final var slot99 = toPaddedSlotKey(99);
        final var slot100 = toPaddedSlotKey(100);

        final var slot0State = domainBuilder
                .contractState()
                .customize(cs -> cs.contractId(contractId).slot(slot0))
                .persist();
        final var slot99State = domainBuilder
                .contractState()
                .customize(cs -> cs.contractId(contractId).slot(slot99))
                .persist();
        domainBuilder
                .contractState()
                .customize(cs -> cs.contractId(contractId).slot(slot100))
                .persist();

        assertThat(contractStateRepository.findInitialStorageSlots(contractId, 99))
                .containsExactlyInAnyOrder(
                        new ContractSlotValue(slot0State.getSlot(), slot0State.getValue()),
                        new ContractSlotValue(slot99State.getSlot(), slot99State.getValue()));
    }

    @Test
    void findStorageBatchDuplicateKeysDoesNotReturnDuplicateValues() {
        final var contractId = domainBuilder.id();
        final var contractSlotsCount = 2;
        final var contractSlotsList = new LinkedList<byte[]>();
        final var contractSlotValuesList = new LinkedList<ContractSlotValue>();
        for (int i = 0; i < contractSlotsCount; i++) {
            final var contractState = domainBuilder
                    .contractState()
                    .customize(cs -> cs.contractId(contractId))
                    .persist();
            contractSlotsList.add(contractState.getSlot());
            contractSlotValuesList.add(new ContractSlotValue(contractState.getSlot(), contractState.getValue()));
        }
        // Duplicate one for the keys
        contractSlotsList.add(contractSlotsList.getFirst());

        assertThat(contractStateRepository.findStorageBatch(contractId, contractSlotsList.toArray(byte[][]::new)))
                .containsAll(contractSlotValuesList);
    }

    private byte[] toPaddedSlotKey(final int index) {
        final var bytes = BigInteger.valueOf(index).toByteArray();
        final var result = new byte[32];
        final var offset = Math.max(0, bytes.length - 32);
        final var length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, offset, result, 32 - length, length);
        return result;
    }
}
