// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static org.hiero.mirror.common.util.DomainUtils.leftPadBytes;

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.service.ContractStateService;

@Named
public class ContractStorageReadableKVState extends AbstractReadableKVState<SlotKey, SlotValue> {

    public static final String KEY = "STORAGE";
    private final ContractStateService contractStateService;

    protected ContractStorageReadableKVState(final ContractStateService contractStateService) {
        super(KEY);
        this.contractStateService = contractStateService;
    }

    @Override
    protected SlotValue readFromDataSource(@Nonnull SlotKey slotKey) {
        if (!slotKey.hasContractID()) {
            return null;
        }

        final var timestamp = ContractCallContext.get().getTimestamp();
        final var contractID = slotKey.contractID();
        final var entityId = EntityIdUtils.entityIdFromContractId(contractID);
        final var keyBytes = slotKey.key().toByteArray();
        return timestamp
                .map(t -> contractStateService.findStorageByBlockTimestamp(
                        entityId, Bytes32.wrap(keyBytes).trimLeadingZeros().toArrayUnsafe(), t))
                .orElse(contractStateService.findStorage(entityId, keyBytes))
                .map(byteArr ->
                        new SlotValue(Bytes.wrap(leftPadBytes(byteArr, Bytes32.SIZE)), Bytes.EMPTY, Bytes.EMPTY))
                .orElse(null);
    }

    @Nonnull
    @Override
    public String getServiceName() {
        return "ContractService";
    }
}
