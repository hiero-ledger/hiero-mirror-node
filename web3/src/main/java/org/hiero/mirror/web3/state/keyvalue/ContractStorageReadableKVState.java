// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema.STORAGE_STATE_ID;
import static org.hiero.mirror.common.util.DomainUtils.leftPadBytes;
import static org.hiero.mirror.web3.state.Utils.contractIdToEvmAddressHex;
import static org.hiero.mirror.web3.state.Utils.hexToSlotValue;
import static org.hiero.mirror.web3.state.Utils.normalizeStorageSlot;

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.inject.Named;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.service.ContractStateService;
import org.jspecify.annotations.NonNull;

@Named
final class ContractStorageReadableKVState extends AbstractReadableKVState<SlotKey, SlotValue> {

    public static final int STATE_ID = STORAGE_STATE_ID;

    private final ContractStateService contractStateService;

    protected ContractStorageReadableKVState(final ContractStateService contractStateService) {
        super(ContractService.NAME, STORAGE_STATE_ID);
        this.contractStateService = contractStateService;
    }

    @Override
    protected SlotValue readFromDataSource(@NonNull SlotKey slotKey) {
        if (!slotKey.hasContractID()) {
            return null;
        }

        // Check storage state overrides before falling through to the DB.
        final var ctx = ContractCallContext.get();
        final var stateOverrides = ctx.getStateOverrides();
        if (!stateOverrides.isEmpty()) {
            final var evmAddr = contractIdToEvmAddressHex(slotKey.contractID());
            if (evmAddr != null) {
                final var override = stateOverrides.get(evmAddr);
                if (override != null) {
                    final var normalizedSlot =
                            normalizeStorageSlot(slotKey.key().toByteArray());
                    if (override.getState() != null) {
                        // Full storage replace: return override value or null (not in override ⇒ slot is empty).
                        final var valueHex = override.getState().get(normalizedSlot);
                        return valueHex != null ? hexToSlotValue(valueHex) : null;
                    } else if (override.getStateDiff() != null) {
                        // Storage patch: return override value if slot is listed, otherwise fall through to DB.
                        final var valueHex = override.getStateDiff().get(normalizedSlot);
                        if (valueHex != null) {
                            return hexToSlotValue(valueHex);
                        }
                    }
                }
            }
        }

        final var timestamp = ctx.getTimestamp();
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

    @Override
    public String getServiceName() {
        return ContractService.NAME;
    }
}
