// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.keyvalue;

import static com.hedera.mirror.common.util.DomainUtils.leftPadBytes;

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.ContractStateRepository;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import org.apache.tuweni.bytes.Bytes32;

@Named
public class ContractStorageReadableKVState extends AbstractReadableKVState<SlotKey, SlotValue> {

    public static final String KEY = "STORAGE";
    private final ContractStateRepository contractStateRepository;

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
                .map(t -> contractStateRepository.findStorageByBlockTimestamp(entityId, keyBytes, t))
                .orElse(contractStateRepository.findStorage(entityId, keyBytes))
                .map(byteArr ->
                        new SlotValue(Bytes.wrap(leftPadBytes(byteArr, Bytes32.SIZE)), Bytes.EMPTY, Bytes.EMPTY))
                .orElse(null);
    }
}
