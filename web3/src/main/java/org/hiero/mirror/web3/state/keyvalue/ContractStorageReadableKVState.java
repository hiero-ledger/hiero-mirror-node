// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema.STORAGE_STATE_ID;
import static org.hiero.mirror.common.util.DomainUtils.leftPadBytes;
import static org.hiero.mirror.web3.state.Utils.contractIdToEvmAddressHex;
import static org.hiero.mirror.web3.state.Utils.hexStringToBytes;

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.inject.Named;
import java.util.Map;
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
                    final var normalizedSlot = normalizeSlot(slotKey.key().toByteArray());
                    if (override.getState() != null) {
                        // Full storage replace: return override value or null (not in override ⇒ slot is empty).
                        final var valueHex = findSlotInMap(override.getState(), normalizedSlot);
                        return valueHex != null ? hexToSlotValue(valueHex) : null;
                    } else if (override.getStateDiff() != null) {
                        // Storage patch: return override value if slot is listed, otherwise fall through to DB.
                        final var valueHex = findSlotInMap(override.getStateDiff(), normalizedSlot);
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

    /**
     * Normalizes a raw slot key byte array to a 64-character lowercase hex string (32 bytes, left-padded).
     */
    private static String normalizeSlot(byte[] slotBytes) {
        var padded = leftPadBytes(slotBytes, Bytes32.SIZE);
        final StringBuilder sb = new StringBuilder(64);
        for (byte b : padded) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Returns the value hex string for {@code normalizedSlot} from {@code storageMap}, or {@code null} if not found.
     * Keys in {@code storageMap} are pre-normalized to 64-character hex by {@code ContractExecutionService}.
     */
    private static String findSlotInMap(@NonNull Map<String, String> storageMap, @NonNull String normalizedSlot) {
        return storageMap.get(normalizedSlot);
    }

    /** Converts a hex value string to a {@link SlotValue} (32-byte left-padded). */
    private static SlotValue hexToSlotValue(@NonNull String hexValue) {
        var valueBytes = hexStringToBytes(hexValue);
        return new SlotValue(Bytes.wrap(leftPadBytes(valueBytes, Bytes32.SIZE)), Bytes.EMPTY, Bytes.EMPTY);
    }

    @Override
    public String getServiceName() {
        return ContractService.NAME;
    }
}
