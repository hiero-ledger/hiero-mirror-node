// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.jspecify.annotations.Nullable;

/** Top-level configuration shared by every bundle in one trace request. */
@Builder
@Value
public class TraceCall {

    @Builder.Default
    @NotNull
    private BlockType block = BlockType.LATEST;

    @Builder.Default
    @NotEmpty
    @NotNull
    private List<@Valid TraceBundle> bundles = List.of();

    private boolean onlyTopCall;

    @Nullable
    @Valid
    private StateOverride stateOverride;

    @NotNull
    private Duration timeout;

    /**
     * n-th position of a transaction in a block, ordered by consensus timestamp. For example {@code 5} loads
     * the state after the first five transactions. {@code -1} uses the block state directly.
     */
    @Builder.Default
    private int transactionIndex = -1;
}
