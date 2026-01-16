// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import lombok.Builder;
import lombok.With;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hyperledger.besu.datatypes.Address;

@Builder
public record ContractFunctionProviderRecord(
        Address contractAddress,
        Address sender,
        long value,
        String expectedErrorMessage,
        @With BlockType block) {

    public static ContractFunctionProviderRecordBuilder builder() {
        return new ContractFunctionProviderRecordBuilder()
                .block(BlockType.LATEST)
                .sender(Address.fromHexString(""));
    }
}
