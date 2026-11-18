// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.throttle;

import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.viewmodel.ContractCallRequest;
import org.jspecify.annotations.Nullable;

public interface ThrottleManager {

    void throttle(ContractCallRequest request);

    void restore(long gas);

    /**
     * Returns the gas an execution did not use, capped at the configured refund share of its gas limit. A failed
     * execution that used its whole gas limit, or one with no result, returns that refund share.
     */
    void restoreUnusedGas(long gasLimit, @Nullable EvmTransactionResult result);
}
