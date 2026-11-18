// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.throttle;

import org.hiero.mirror.web3.viewmodel.ContractCallRequest;

public interface ThrottleManager {

    void throttle(ContractCallRequest request);

    void restore(long gas);

    /**
     * Returns the refundable gas of an execution, capped at the configured refund share of its gas limit.
     */
    void restoreRefundableGas(long gasLimit, long refundableGas);
}
