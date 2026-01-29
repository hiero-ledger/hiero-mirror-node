// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import lombok.Builder;
import lombok.Value;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.TracerType;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hyperledger.besu.datatypes.Address;

@Value
@Builder
public class ContractExecutionParameters implements CallServiceParameters {
    private final BlockType block;
    private final Bytes callData;
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
    public Bytes getEthereumData() {
        return null;
    }
}
