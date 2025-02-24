// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.utils;

import com.hedera.mirror.web3.viewmodel.BlockType;
import lombok.Builder;
import lombok.With;
import org.hyperledger.besu.datatypes.Address;

@Builder
public record ContractFunctionProviderRecord(
        Address contractAddress, Address sender, long value, String expectedErrorMessage, @With BlockType block) {

    public static ContractFunctionProviderRecordBuilder builder() {
        return new ContractFunctionProviderRecordBuilder()
                .block(BlockType.LATEST)
                .sender(Address.fromHexString(""));
    }
}
