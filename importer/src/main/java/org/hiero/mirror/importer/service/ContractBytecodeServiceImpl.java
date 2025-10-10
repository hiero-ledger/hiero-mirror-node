// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.service;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.util.encoders.Hex;
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

            return Hex.decode(stripHexPrefix(hexBytecode));
        } catch (Exception e) {
            Utility.handleRecoverableError("Failed to decode contract bytecode from file {}", fileId, e);
            return null;
        }
    }

    private static byte[] stripHexPrefix(byte[] data) {
        // If the first two bytes are hex prefix '0x', strip them
        if (data.length >= 2 && data[0] == (byte) 0x30 && data[1] == (byte) 0x78) {
            return ArrayUtils.subarray(data, 2, data.length);
        }

        return data;
    }
}
