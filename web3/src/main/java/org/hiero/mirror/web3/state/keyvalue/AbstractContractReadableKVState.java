// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.services.utils.EntityIdUtils.entityIdFromContractId;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.hyperledger.besu.datatypes.Address;
import org.jspecify.annotations.NonNull;

/**
 * Base class for contract-related {@link AbstractReadableKVState} implementations
 */
abstract class AbstractContractReadableKVState<K, V> extends AbstractReadableKVState<K, V> {

    protected final CommonEntityAccessor commonEntityAccessor;

    protected AbstractContractReadableKVState(
            @NonNull final String serviceName,
            final int stateId,
            @NonNull final CommonEntityAccessor commonEntityAccessor) {
        super(serviceName, stateId);
        this.commonEntityAccessor = commonEntityAccessor;
    }

    /**
     * Resolves the state override (if any) keyed by the contract's EVM address. A {@link ContractID} may reference a
     * contract either by its number or directly by an EVM address
     */
    protected StateOverride findStateOverride(
            @NonNull final ContractCallContext context, @NonNull final ContractID contractID) {
        final var stateOverrides = context.getStateOverrides();
        if (stateOverrides == null || stateOverrides.isEmpty()) {
            return null;
        }

        // A ContractID supplied directly with an EVM address is keyed by that address.
        if (contractID.evmAddress() != null && !Bytes.EMPTY.equals(contractID.evmAddress())) {
            return stateOverrides.get(contractID.evmAddress());
        }

        // Numeric ContractID: try the long-zero (mirror) address derived from the contract number first.
        final var override = stateOverrides.get(Bytes.wrap(toEvmAddress(contractID.contractNum())));
        if (override != null) {
            return override;
        }

        // Otherwise the contract may have an EVM alias the override is keyed by; resolve it and retry.
        final var entityId = entityIdFromContractId(contractID);
        if (!EntityId.EMPTY.equals(entityId)) {
            final var aliasAddress = commonEntityAccessor.evmAddressFromId(entityId, context.getTimestamp());
            if (aliasAddress != null && !Address.ZERO.equals(aliasAddress)) {
                return stateOverrides.get(Bytes.wrap(aliasAddress.toArrayUnsafe()));
            }
        }
        return null;
    }
}
