// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
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
    private final boolean isEstimate;
    private final boolean isModularized;
    private final boolean isStatic;
    private final Address receiver;
    private final HederaEvmAccount sender;
    private final TracerType tracerType = TracerType.OPERATION;
    private final long value;
}
