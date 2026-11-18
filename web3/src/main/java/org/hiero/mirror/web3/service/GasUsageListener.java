// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.jspecify.annotations.Nullable;

/**
 * Receives the gas limit and result of each EVM execution, so the controller that reserved throttle gas for it can
 * return the gas it did not use. The result is null when the execution failed before producing one. Gas estimation
 * search iterations are not reported, because no gas was reserved for them.
 */
@FunctionalInterface
public interface GasUsageListener {

    /** For callers that did not reserve throttle gas. */
    GasUsageListener NONE = (gasLimit, result) -> {};

    void executed(long gasLimit, @Nullable EvmTransactionResult result);
}
