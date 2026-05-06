// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;

@Value
@RequiredArgsConstructor
public class OpcodeRequest {

    @NotNull
    TransactionIdOrHashParameter transactionIdOrHashParameter;

    boolean stack;
    boolean memory;
    boolean storage;
}
