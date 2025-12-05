// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.TracerType;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hyperledger.besu.datatypes.Address;

@Value
@Builder(toBuilder = true)
@RequiredArgsConstructor
public class ContractDebugParameters implements CallServiceParameters {
    @NotNull
    BlockType block;

    @NotNull
    Bytes callData;

    @NotNull
    CallType callType = CallType.ETH_DEBUG_TRACE_TRANSACTION;

    @Positive
    long consensusTimestamp;

    @PositiveOrZero
    long gas;

    @PositiveOrZero
    long gasPrice;

    @AssertFalse
    boolean isEstimate = false;

    boolean isModularized;

    @AssertFalse
    boolean isStatic = false;

    @NotNull
    Address receiver;

    @NotNull
    Address sender;

    @NotNull
    TracerType tracerType = TracerType.OPCODE;

    @PositiveOrZero
    long value;
}
