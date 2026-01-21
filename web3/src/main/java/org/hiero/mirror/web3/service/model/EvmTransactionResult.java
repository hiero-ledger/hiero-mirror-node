// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;

public record EvmTransactionResult(ResponseCodeEnum responseCodeEnum, ContractFunctionResult functionResult) {

    public Optional<Bytes> getErrorMessage() {
        return functionResult.errorMessage().startsWith(HEX_PREFIX)
                ? Optional.of(Bytes.fromHexString(functionResult.errorMessage()))
                : Optional.empty(); // If it doesn't start with 0x, the message is already decoded and readable.
    }

    public String contractCallResult() {
        return functionResult.contractCallResult().toHex();
    }

    public boolean isSuccessful() {
        return responseCodeEnum.equals(ResponseCodeEnum.SUCCESS);
    }

    public long gasUsed() {
        return functionResult.gasUsed();
    }
}
