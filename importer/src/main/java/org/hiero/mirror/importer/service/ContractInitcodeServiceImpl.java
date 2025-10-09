// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.service;

import com.hedera.services.stream.proto.ContractBytecode;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.util.Utility;

@Named
@RequiredArgsConstructor
public final class ContractInitcodeServiceImpl implements ContractInitcodeService {

    private final ContractBytecodeService contractBytecodeService;

    @Override
    public byte[] get(ContractBytecode contractBytecode, RecordItem recordItem) {
        if (!recordItem.getTransactionBody().hasContractCreateInstance()) {
            return null;
        }

        var contractCreate = recordItem.getTransactionBody().getContractCreateInstance();
        if (contractCreate.hasInitcode()) {
            return DomainUtils.toBytes(contractCreate.getInitcode());
        } else if (contractCreate.hasFileID() && recordItem.isBlockstream()) {
            var fileId = EntityId.of(contractCreate.getFileID());
            byte[] initcode = contractBytecodeService.get(fileId);
            if (initcode == null) {
                Utility.handleRecoverableError(
                        "Failed to get initcode from file {} at {}", fileId, recordItem.getConsensusTimestamp());
            }

            return initcode;
        }

        if (contractBytecode != null) {
            return DomainUtils.toBytes(contractBytecode.getInitcode());
        }

        return null;
    }
}
