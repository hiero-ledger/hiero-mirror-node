// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import static org.hiero.mirror.web3.validation.HexValidator.HEX_PREFIX;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.apache.commons.codec.binary.Hex;
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
    byte[] callDataBytes;

    byte[] ethereumDataBytes;

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

    @Override
    public String getCallData() {
        return callDataBytes != null && callDataBytes.length > 0
                ? HEX_PREFIX + Hex.encodeHexString(callDataBytes)
                : HEX_PREFIX;
    }

    @Override
    public String getEthereumData() {
        return ethereumDataBytes != null && ethereumDataBytes.length > 0
                ? HEX_PREFIX + Hex.encodeHexString(ethereumDataBytes)
                : HEX_PREFIX;
    }
}
