// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum TracerType {
    @JsonProperty("callTracer")
    ACTION,
    @JsonProperty("opcodeLogger")
    OPCODE,
    OPERATION
}
