// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.StateOverride;

/**
 * One trace request. Built from an already validated {@link org.hiero.mirror.web3.viewmodel.ActionTraceRequest}, so
 * it carries no validation of its own. Bundles are an API concept only: their transactions run in request order, so
 * they are kept as one list.
 */
@Builder
@Value
public class TraceCall {

    @Builder.Default
    private BlockType block = BlockType.LATEST;

    private boolean onlyTopCall;

    @Builder.Default
    private List<StateOverride> stateOverrides = List.of();

    @Builder.Default
    private List<ContractExecutionParameters> transactions = List.of();

    /**
     * Position of an EVM transaction in {@link #block}, ordered by consensus timestamp. {@code 0} starts the simulation
     * at the beginning of the block, before its first transaction. A positive value {@code N} reads state one
     * nanosecond after the Nth EVM transaction. {@code -1} starts the simulation after every transaction in the block.
     * It has no effect when {@link #block} is {@code latest}. The timestamp applies to the whole request.
     */
    @Builder.Default
    private int transactionIndex = -1;
}
