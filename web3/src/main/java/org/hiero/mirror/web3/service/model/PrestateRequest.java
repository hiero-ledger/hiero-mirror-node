// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import jakarta.validation.constraints.NotNull;
import org.hiero.mirror.web3.common.TransactionIdOrHashParameter;

public record PrestateRequest(
        @NotNull TransactionIdOrHashParameter transactionIdOrHashParameter,
        boolean diffMode,
        boolean code,
        boolean storage) {}
