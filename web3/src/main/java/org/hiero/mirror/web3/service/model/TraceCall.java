// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.MAX_TRACE_BUNDLES;
import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.MAX_TRACE_TRANSACTIONS;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.StateOverride;

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
    @Size(max = MAX_TRACE_BUNDLES)
    private List<@NotNull @Valid TraceBundle> bundles = List.of();

    private boolean onlyTopCall;

    @Builder.Default
    @NotNull
    @Size(max = 10)
    private List<@NotNull @Valid StateOverride> stateOverrides = List.of();

    @NotNull
    @DurationMin(millis = 1)
    @DurationMax(seconds = 10)
    private Duration timeout;

    /**
     * Position of a transaction in the block, ordered by consensus timestamp. {@code 0} starts the simulation at the
     * beginning of the block, before its first transaction. A positive value is that transaction's consensus
     * timestamp. {@code -1} starts the simulation after every transaction in the block. The timestamp applies to the
     * whole request.
     */
    @Builder.Default
    private int transactionIndex = -1;

    @AssertTrue(message = "transactions field size must be between 1 and " + MAX_TRACE_TRANSACTIONS)
    private boolean hasTransactionLimit() {
        if (bundles == null) {
            return true;
        }
        long count = 0;
        for (final var bundle : bundles) {
            if (bundle != null && bundle.getTransactions() != null) {
                count += bundle.getTransactions().size();
                if (count > MAX_TRACE_TRANSACTIONS) {
                    return false;
                }
            }
        }
        return true;
    }
}
