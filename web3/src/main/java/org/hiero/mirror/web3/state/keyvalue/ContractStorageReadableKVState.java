// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema.STORAGE_STATE_ID;
import static org.hiero.mirror.common.util.DomainUtils.bytesToHex;
import static org.hiero.mirror.common.util.DomainUtils.leftPadBytes;
import static org.hiero.mirror.web3.convert.BytesDecoder.hexToBytes;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.inject.Named;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.service.ContractStateService;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.hiero.mirror.web3.viewmodel.StorageEntry;
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

        final var context = ContractCallContext.get();
        final var override = findStateOverride(context, slotKey.contractID());
        if (override != null) {
            // Replace all slots from state overrides and don't go to DB, if set.
            if (override.getState() != null) {
                return slotValueFromStateOverrides(override.getState(), slotKey);
            }
            // Replace only the existing slots from state_diff overrides, if set.
            if (override.getStateDiff() != null) {
                final var patched = slotValueFromStateOverrides(override.getStateDiff(), slotKey);
                // Return the patched value only if found.
                if (patched != null) {
                    return patched;
                }
            }
        }

        return readStorageFromDatabase(context, slotKey);
    }

    @Override
    public String getServiceName() {
        return ContractService.NAME;
    }

    private StateOverride findStateOverride(@NonNull ContractCallContext context, @NonNull ContractID contractID) {
        final var stateOverrides = context.getStateOverrides();
        if (stateOverrides == null || stateOverrides.isEmpty()) {
            return null;
        }
        final var evmAddr = toAddress(contractID.contractNum()).toHexString();
        return stateOverrides.get(evmAddr);
    }

    private SlotValue readStorageFromDatabase(@NonNull ContractCallContext context, @NonNull SlotKey slotKey) {
        final var contractID = slotKey.contractID();
        final var entityId = EntityIdUtils.entityIdFromContractId(contractID);
        final var keyBytes = slotKey.key().toByteArray();
        final var timestamp = context.getTimestamp();
        return timestamp
                .map(t -> contractStateService.findStorageByBlockTimestamp(
                        entityId, Bytes32.wrap(keyBytes).trimLeadingZeros().toArrayUnsafe(), t))
                .orElse(contractStateService.findStorage(entityId, keyBytes))
                .map(byteArr ->
                        new SlotValue(Bytes.wrap(leftPadBytes(byteArr, Bytes32.SIZE)), Bytes.EMPTY, Bytes.EMPTY))
                .orElse(null);
    }

    private SlotValue slotValueFromStateOverrides(@NonNull List<StorageEntry> storage, @NonNull SlotKey slotKey) {
        final var slotHex = storageSlotHex(slotKey);
        for (var entry : storage) {
            if (slotHex.equals(entry.getKey())) {
                return hexToSlotValue(entry.getValue());
            }
        }
        return null;
    }

    private String storageSlotHex(@NonNull SlotKey slotKey) {
        return HEX_PREFIX + bytesToHex(leftPadBytes(slotKey.key().toByteArray(), Bytes32.SIZE));
    }

    /** Converts a validated 32-byte hex value string to a {@link SlotValue}. */
    private SlotValue hexToSlotValue(@NonNull String hexValue) {
        return new SlotValue(Bytes.wrap(hexToBytes(hexValue)), Bytes.EMPTY, Bytes.EMPTY);
    }
}
