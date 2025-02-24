// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.keyvalue;

import static com.hedera.mirror.web3.state.Utils.isMirror;
import static com.hedera.services.utils.EntityIdUtils.entityIdFromContractId;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.web3.repository.ContractRepository;
import com.hedera.mirror.web3.state.CommonEntityAccessor;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Optional;

@Named
public class ContractBytecodeReadableKVState extends AbstractReadableKVState<ContractID, Bytecode> {

    public static final String KEY = "BYTECODE";
    private final ContractRepository contractRepository;

    private final CommonEntityAccessor commonEntityAccessor;

    protected ContractBytecodeReadableKVState(
            final ContractRepository contractRepository, CommonEntityAccessor commonEntityAccessor) {
        super(KEY);
        this.contractRepository = contractRepository;
        this.commonEntityAccessor = commonEntityAccessor;
    }

    @Override
    protected Bytecode readFromDataSource(@Nonnull ContractID contractID) {
        final var entityId = toEntityId(contractID);

        return contractRepository
                .findRuntimeBytecode(entityId.getId())
                .map(Bytes::wrap)
                .map(Bytecode::new)
                .orElse(null);
    }

    private EntityId toEntityId(@Nonnull final com.hedera.hapi.node.base.ContractID contractID) {
        if (contractID.hasContractNum()) {
            return entityIdFromContractId(contractID);
        } else if (contractID.hasEvmAddress()) {
            final var evmAddress = contractID.evmAddress().toByteArray();
            if (isMirror(evmAddress)) {
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
}
