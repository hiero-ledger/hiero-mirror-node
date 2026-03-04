// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.TracerType;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hyperledger.besu.datatypes.Address;

@Value
@Builder
public class ContractExecutionParameters implements CallServiceParameters {
    private final BlockType block;

    @Builder.Default
    private final String callData = HEX_PREFIX;

    private final CallType callType;
    private final long gas;
    private final long gasPrice;
    private final boolean isEstimate;
    private final boolean isStatic;
    private final Address receiver;
    private final Address sender;
    private final TracerType tracerType = TracerType.OPERATION;
    private final long value;

    @Override
    public String getEthereumData() {
        return HEX_PREFIX;
    }
}
