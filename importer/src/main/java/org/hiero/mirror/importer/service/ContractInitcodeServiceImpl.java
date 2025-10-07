// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.service;

import com.hedera.services.stream.proto.ContractBytecode;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;

@Named
@RequiredArgsConstructor
public final class ContractInitcodeServiceImpl implements ContractInitcodeService {

    private final FileDataService fileDataService;

    @Override
    public byte[] get(ContractBytecode contractBytecode, RecordItem recordItem) {
        if (!recordItem.getTransactionBody().hasContractCreateInstance()) {
            return null;
        }

        if (contractBytecode != null && !contractBytecode.getInitcode().isEmpty()) {
            return DomainUtils.toBytes(contractBytecode.getInitcode());
        }

        var contractCreate = recordItem.getTransactionBody().getContractCreateInstance();
        if (contractCreate.hasInitcode()) {
            return DomainUtils.toBytes(contractCreate.getInitcode());
        } else if (contractCreate.hasFileID() && recordItem.isBlockstream()) {
            return fileDataService.get(recordItem.getConsensusTimestamp(), EntityId.of(contractCreate.getFileID()));
        }

        return null;
    }
}
