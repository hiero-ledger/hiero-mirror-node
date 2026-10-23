// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.MAX_TRACE_TRANSACTIONS;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.jspecify.annotations.Nullable;

/** Transactions that share one read/write cache and this bundle's block override. */
@Builder
@Value
public class TraceBundle {

    @Nullable
    @Valid
    private BlockOverride blockOverride;

    @Builder.Default
    @NotEmpty
    @NotNull
    @Size(max = MAX_TRACE_TRANSACTIONS)
    private List<@NotNull @Valid ContractExecutionParameters> transactions = List.of();
}
