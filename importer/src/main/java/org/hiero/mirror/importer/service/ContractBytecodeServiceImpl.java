// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.service;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.importer.util.Utility;

@Named
@RequiredArgsConstructor
class ContractBytecodeServiceImpl implements ContractBytecodeService {

    private final FileDataService fileDataService;

    @Override
    public byte[] get(EntityId fileId) {
        try {
            byte[] hexBytecode = fileDataService.get(fileId);
            if (hexBytecode == null) {
                return null;
            }

            return Utility.decodeBytecode(hexBytecode);
        } catch (Exception e) {
            Utility.handleRecoverableError("Failed to decode contract bytecode from file {}", fileId, e);
            return null;
        }
    }
}
