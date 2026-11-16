// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.services.utils.EntityIdUtils.entityIdFromContractId;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
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

        // The account may already be cached for this call with its EVM alias, e.g. the dummy account created for an
        // overridden EVM address that has no entity in the DB, so it cannot be resolved from the DB below.
        final var cachedAccount = context.getReadCacheState(AccountReadableKVState.STATE_ID)
                .get(AccountID.newBuilder()
                        .shardNum(contractID.shardNum())
                        .realmNum(contractID.realmNum())
                        .accountNum(contractID.contractNumOrElse(0L))
                        .build());
        if (cachedAccount instanceof Account account && ConversionUtils.isEvmAddress(account.alias())) {
            return stateOverrides.get(account.alias());
        }

        // Otherwise the contract may have an EVM alias the override is keyed by; resolve it and retry.
        final var entityId = entityIdFromContractId(contractID);
        if (!EntityId.EMPTY.equals(entityId)) {
            final var aliasAddress = commonEntityAccessor.evmAddressFromId(entityId, context.getTimestamp());
            if (aliasAddress != null && !Address.ZERO.equals(aliasAddress)) {
                return stateOverrides.get(Bytes.wrap(aliasAddress.getBytes().toArrayUnsafe()));
            }
        }
        return null;
    }
}
