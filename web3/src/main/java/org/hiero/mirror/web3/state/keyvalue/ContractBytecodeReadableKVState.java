// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema.BYTECODE_STATE_ID;
import static com.hedera.services.utils.EntityIdUtils.entityIdFromContractId;
import static org.hiero.mirror.common.util.DomainUtils.isLongZeroAddress;
import static org.hiero.mirror.web3.convert.BytesDecoder.hexToBytes;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.ContractRepository;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.jspecify.annotations.NonNull;

@Named
final class ContractBytecodeReadableKVState extends AbstractContractReadableKVState<ContractID, Bytecode> {

    public static final int STATE_ID = BYTECODE_STATE_ID;

    private final ContractRepository contractRepository;

    ContractBytecodeReadableKVState(
            final ContractRepository contractRepository, CommonEntityAccessor commonEntityAccessor) {
        super(ContractService.NAME, STATE_ID, commonEntityAccessor);
        this.contractRepository = contractRepository;
    }

    @Override
    protected Bytecode readFromDataSource(@NonNull ContractID contractID) {
        // Check code override first so it takes precedence over DB bytecode.
        final var stateOverride = applyStateOverride(contractID);
        if (stateOverride != null) {
            return stateOverride;
        }

        final var entityId = toEntityId(contractID);

        return contractRepository
                .findRuntimeBytecode(entityId.getId())
                .map(Bytes::wrap)
                .map(Bytecode::new)
                .orElse(null);
    }

    private Bytecode applyStateOverride(@NonNull ContractID contractID) {
        final var ctx = ContractCallContext.get();
        final var stateOverrides = ctx.getStateOverrides();
        if (stateOverrides == null || stateOverrides.isEmpty()) {
            return null;
        }

        final var stateOverride = findStateOverride(ctx, contractID);
        if (stateOverride != null && stateOverride.getCode() != null) {
            final var bytecode = new Bytecode(Bytes.wrap(hexToBytes(stateOverride.getCode())));
            // The contract has no bytecode in the DB (or it is being overridden), so persist the
            // overridden bytecode into the write cache
            ctx.getWriteCacheState(STATE_ID).put(contractID, bytecode);
            return bytecode;
        }
        return null;
    }

    private EntityId toEntityId(@NonNull final ContractID contractID) {
        if (contractID.hasContractNum()) {
            return entityIdFromContractId(contractID);
        } else if (contractID.hasEvmAddress()) {
            final var evmAddress = contractID.evmAddress().toByteArray();
            if (isLongZeroAddress(evmAddress)) {
                return DomainUtils.fromEvmAddress(evmAddress);
            } else {
                return commonEntityAccessor
                        .getEntityByEvmAddressAndTimestamp(evmAddress, Optional.empty())
                        .map(Entity::toEntityId)
                        .orElse(EntityId.EMPTY);
            }
        }
        return EntityId.EMPTY;
    }

    @Override
    public String getServiceName() {
        return ContractService.NAME;
    }
}
