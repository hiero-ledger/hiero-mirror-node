// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import java.time.Duration;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.StateOverride;

/**
 * Top-level configuration shared by every bundle in one trace request. Built from an already validated
 * {@link org.hiero.mirror.web3.viewmodel.ActionTraceRequest}, so it carries no validation of its own.
 */
@Builder
@Value
public class TraceCall {

    @Builder.Default
    private BlockType block = BlockType.LATEST;

    @Builder.Default
    private List<TraceBundle> bundles = List.of();

    private boolean onlyTopCall;

    @Builder.Default
    private List<StateOverride> stateOverrides = List.of();

    private Duration timeout;

    /**
     * Position of an EVM transaction in {@link #block}, ordered by consensus timestamp. {@code 0} starts the simulation
     * at the beginning of the block, before its first transaction. A positive value {@code N} reads state one
     * nanosecond after the Nth EVM transaction. {@code -1} starts the simulation after every transaction in the block.
     * It has no effect when {@link #block} is {@code latest}. The timestamp applies to the whole request.
     */
    @Builder.Default
    private int transactionIndex = -1;
}
