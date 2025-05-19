// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.TracerType;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hyperledger.besu.datatypes.Address;

public interface CallServiceParameters {
    BlockType getBlock();

    Bytes getCallData();

    CallType getCallType();

    long getGas();

    Address getReceiver();

    HederaEvmAccount getSender();

    TracerType getTracerType();

    long getValue();

    boolean isEstimate();

    boolean isModularized();

    boolean isStatic();

    enum CallType {
        ETH_CALL,
        ETH_DEBUG_TRACE_TRANSACTION,
        ETH_ESTIMATE_GAS
    }
}
