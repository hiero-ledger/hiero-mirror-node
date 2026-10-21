// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
    private List<ContractExecutionParameters> transactions = List.of();
}
