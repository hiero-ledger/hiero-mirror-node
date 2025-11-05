// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.service;

import com.google.common.primitives.Bytes;
import com.hedera.services.stream.proto.ContractBytecode;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Named
@NullMarked
@RequiredArgsConstructor
public final class ContractInitcodeServiceImpl implements ContractInitcodeService {

    private final ContractBytecodeService contractBytecodeService;

    @Override
    public byte @Nullable [] get(@Nullable ContractBytecode contractBytecode, RecordItem recordItem) {
        if (!recordItem.getTransactionBody().hasContractCreateInstance()) {
            return null;
        }

        var contractCreate = recordItem.getTransactionBody().getContractCreateInstance();
        if (contractCreate.hasInitcode()) {
            return DomainUtils.toBytes(contractCreate.getInitcode());
        } else if (contractCreate.hasFileID() && recordItem.isBlockstream()) {
            final var fileId = EntityId.of(contractCreate.getFileID());
            final byte[] initcode = contractBytecodeService.get(fileId);
            if (initcode == null) {
                Utility.handleRecoverableError(
                        "Failed to get initcode from file {} at {}", fileId, recordItem.getConsensusTimestamp());
                return null;
            }

            final var constructorParameters = contractCreate.getConstructorParameters();
            return constructorParameters.isEmpty()
                    ? initcode
                    : Bytes.concat(initcode, DomainUtils.toBytes(constructorParameters));
        }

        if (contractBytecode != null) {
            return DomainUtils.toBytes(contractBytecode.getInitcode());
        }

        return null;
    }
}
