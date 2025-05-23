// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static org.hiero.mirror.common.util.DomainUtils.leftPadBytes;

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.service.model.ContractSlotValue;

@Named
public class ContractStorageReadableKVState extends AbstractReadableKVState<SlotKey, SlotValue> {

    public static final String KEY = "STORAGE";
    private final ContractStateRepository contractStateRepository;
    private Map<Long, List<ContractSlotValue>> contractsSlotsValues = new HashMap<>();

    protected ContractStorageReadableKVState(final ContractStateRepository contractStateRepository) {
        super(KEY);
        this.contractStateRepository = contractStateRepository;
    }

    @Override
    protected SlotValue readFromDataSource(@Nonnull SlotKey slotKey) {
        if (!slotKey.hasContractID()) {
            return null;
        }

        final var timestamp = ContractCallContext.get().getTimestamp();
        final var contractID = slotKey.contractID();
        final var entityId = EntityIdUtils.entityIdFromContractId(contractID).getId();
        final var keyBytes = slotKey.key().toByteArray();
        return timestamp
                .map(t -> contractStateRepository.findStorageByBlockTimestamp(
                        entityId, Bytes32.wrap(keyBytes).trimLeadingZeros().toArrayUnsafe(), t))
                .orElse(findSlotValue(entityId, keyBytes))
                .map(byteArr ->
                        new SlotValue(Bytes.wrap(leftPadBytes(byteArr, Bytes32.SIZE)), Bytes.EMPTY, Bytes.EMPTY))
                .orElse(null);
    }

    private List<ContractSlotValue> findContractSlotsValues(Long entityId) {
        ContractCallContext context = ContractCallContext.get();
        contractsSlotsValues = context.getContractsSlotsValues();
        if (!contractsSlotsValues.containsKey(entityId)) {
            List<ContractSlotValue> contractSlotValues = contractStateRepository.findSlotsValuesByContractId(entityId);
            contractsSlotsValues.put(entityId, contractSlotValues);
            return contractSlotValues;
        } else {
            return contractsSlotsValues.get(entityId);
        }
    }

    private Optional<byte[]> findSlotValue(Long entityId, byte[] slotKeyByteArray) {
        List<ContractSlotValue> contractSlotsValues = findContractSlotsValues(entityId);
        Optional<ContractSlotValue> contractSlotValue = contractSlotsValues.stream()
                .filter(sv -> Arrays.equals(sv.slot(), slotKeyByteArray))
                .findFirst();
        if (contractSlotValue.isPresent()) {
            return Optional.of(contractSlotValue.get().value());
        }
        return contractStateRepository.findStorage(entityId, slotKeyByteArray);
    }
}
