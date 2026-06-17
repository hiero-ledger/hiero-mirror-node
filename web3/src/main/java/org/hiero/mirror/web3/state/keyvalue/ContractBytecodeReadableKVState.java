// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema.BYTECODE_STATE_ID;
import static com.hedera.services.utils.EntityIdUtils.entityIdFromContractId;
import static org.hiero.mirror.common.util.DomainUtils.isLongZeroAddress;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
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
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.jspecify.annotations.NonNull;

@Named
final class ContractBytecodeReadableKVState extends AbstractReadableKVState<ContractID, Bytecode> {

    public static final int STATE_ID = BYTECODE_STATE_ID;

    private final ContractRepository contractRepository;

    private final CommonEntityAccessor commonEntityAccessor;

    protected ContractBytecodeReadableKVState(
            final ContractRepository contractRepository, CommonEntityAccessor commonEntityAccessor) {
        super(ContractService.NAME, STATE_ID);
        this.contractRepository = contractRepository;
        this.commonEntityAccessor = commonEntityAccessor;
    }

    @Override
    protected Bytecode readFromDataSource(@NonNull ContractID contractID) {
        final var entityId = toEntityId(contractID);
        // Check code override first so it takes precedence over DB bytecode.
        final var stateOverride = applyStateOverride(contractID);
        if (stateOverride != null) {
            return stateOverride;
        }

        return contractRepository
                .findRuntimeBytecode(entityId.getId())
                .map(Bytes::wrap)
                .map(Bytecode::new)
                .orElse(null);
    }

    private Bytecode applyStateOverride(@NonNull ContractID contractID) {
        final var ctx = ContractCallContext.get();
        final var stateOverrides = ctx.getStateOverrides();
        if (stateOverrides != null && !stateOverrides.isEmpty()) {
            final Bytes contractAddress;
            StateOverride stateOverride;
            if (contractID.evmAddress() != null && !Bytes.EMPTY.equals(contractID.evmAddress())) {
                contractAddress = contractID.evmAddress();
                stateOverride = stateOverrides.get(contractAddress);
            } else {
                contractAddress = Bytes.wrap(toEvmAddress(contractID.contractNum()));
                stateOverride = stateOverrides.get(contractAddress);
            }

            if (stateOverride != null && stateOverride.getCode() != null) {
                return new Bytecode(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(hexToBytes(stateOverride.getCode())));
            }
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
