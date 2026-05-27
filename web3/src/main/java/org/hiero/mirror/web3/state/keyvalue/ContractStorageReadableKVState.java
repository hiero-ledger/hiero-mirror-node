// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema.STORAGE_STATE_ID;
import static org.hiero.mirror.common.util.DomainUtils.bytesToHex;
import static org.hiero.mirror.common.util.DomainUtils.leftPadBytes;
import static org.hiero.mirror.web3.state.Utils.*;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.inject.Named;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.service.ContractStateService;
import org.hiero.mirror.web3.viewmodel.NormalizedStateOverride;
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
                return slotValueFromMap(override.getState(), slotKey);
            }
            // Replace only the existing slots from state_diff overrides, if set.
            if (override.getStateDiff() != null) {
                final var patched = slotValueFromMap(override.getStateDiff(), slotKey);
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

    private NormalizedStateOverride findStateOverride(
            @NonNull ContractCallContext context, @NonNull ContractID contractID) {
        final var stateOverrides = context.getStateOverrides();
        if (stateOverrides.isEmpty()) {
            return null;
        }
        final var evmAddr = contractIdToEvmAddressHex(contractID);
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

    /**
     * Looks up a slot in a storage override map. Returns {@code null} when the slot is absent
     * ({@code state} replace) or when the slot is not listed ({@code state_diff} patch).
     */
    private SlotValue slotValueFromMap(@NonNull Map<String, String> storage, @NonNull SlotKey slotKey) {
        final var valueHex =
                storage.get(normalizeStorageSlot(bytesToHex(slotKey.key().toByteArray())));
        return valueHex != null ? hexToSlotValue(valueHex) : null;
    }

    /** Converts a hex value string to a {@link SlotValue} (32-byte left-padded). */
    private SlotValue hexToSlotValue(@NonNull String hexValue) {
        final var decoded = decodeHex(hexValue);
        final var padded = decoded.length >= Bytes32.SIZE ? decoded : DomainUtils.leftPadBytes(decoded, Bytes32.SIZE);
        return new SlotValue(Bytes.wrap(padded), Bytes.EMPTY, Bytes.EMPTY);
    }
}
