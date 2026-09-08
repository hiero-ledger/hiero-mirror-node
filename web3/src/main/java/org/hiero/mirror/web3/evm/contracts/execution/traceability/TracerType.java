// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum TracerType {
    @JsonProperty("callTracer")
    ACTION,
    @JsonProperty("keccak256PreimageTracer")
    KECCAK256_PREIMAGE,
    @JsonProperty("opcodeLogger")
    OPCODE,
    OPERATION,
    @JsonProperty("prestateTracer")
    PRESTATE
}
