// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.test.e2e.acceptance.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hedera.mirror.rest.model.ContractCallRequest;

/**
 * A temporary extension of {@link ContractCallRequest} that forces Web3 to execute
 * using the modularized path when {@code isModularized} is set to {@code true},
 * regardless of the configured traffic percentage split.
 * <p>
 *
 * This class is used for testing and transitional purposes and should be removed
 * once modularized execution is fully integrated into the system.
 */
public class ContractCallModularizedRequest extends ContractCallRequest {

    @JsonProperty("isModularized")
    private final Boolean isModularized;

    public ContractCallModularizedRequest(ContractCallRequest contractCallRequest) {
        // Copy values from the provided ContractCallRequest
        this.setBlock(contractCallRequest.getBlock());
        this.setData(contractCallRequest.getData());
        this.setEstimate(contractCallRequest.getEstimate());
        this.setFrom(contractCallRequest.getFrom());
        this.setGas(contractCallRequest.getGas());
        this.setGasPrice(contractCallRequest.getGasPrice());
        this.setTo(contractCallRequest.getTo());
        this.setValue(contractCallRequest.getValue());
        this.isModularized = true;
    }
}
