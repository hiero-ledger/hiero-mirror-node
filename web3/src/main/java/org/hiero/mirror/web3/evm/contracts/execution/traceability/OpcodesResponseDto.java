// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Lightweight response DTO that directly holds internal {@link Opcode} records,
 * avoiding a per-element copy into the generated API model. Jackson serializes
 * this to the same JSON shape as the OpenAPI-generated OpcodesResponse.
 */
public record OpcodesResponseDto(
        String address,
        @JsonProperty("contract_id") String contractId,
        boolean failed,
        long gas,
        List<Opcode> opcodes,
        @JsonProperty("return_value") String returnValue) {}
