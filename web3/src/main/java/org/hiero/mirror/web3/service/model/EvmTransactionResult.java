// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import java.util.Optional;

public record EvmTransactionResult(ResponseCodeEnum responseCodeEnum, ContractFunctionResult functionResult) {

    public Optional<String> getErrorMessage() {
        if (functionResult == null) {
            return Optional.empty();
        }
        var errorMessage = functionResult.errorMessage();
        if (errorMessage.isEmpty()) {
            return Optional.empty();
        }
        return errorMessage.startsWith(HEX_PREFIX) ? Optional.of(errorMessage) : Optional.empty();
    }

    public String contractCallResult() {
        if (functionResult == null) {
            return HEX_PREFIX;
        }
        var result = functionResult.contractCallResult();
        if (result.length() == 0) {
            return HEX_PREFIX;
        }
        var hex = result.toHex();
        if (hex == null || hex.isEmpty()) {
            return HEX_PREFIX;
        }
        return hex.startsWith(HEX_PREFIX) ? hex : HEX_PREFIX + hex;
    }

    public boolean isSuccessful() {
        return responseCodeEnum != null && responseCodeEnum.equals(ResponseCodeEnum.SUCCESS);
    }

    public long gasUsed() {
        return functionResult != null ? functionResult.gasUsed() : 0L;
    }
}
