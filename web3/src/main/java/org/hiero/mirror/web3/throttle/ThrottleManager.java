// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.throttle;

import org.hiero.mirror.web3.viewmodel.ContractCallRequest;

public interface ThrottleManager {

    void restore(long gas);

    /** Returns one global requests-per-second token taken by {@link #throttle(ContractCallRequest)}. */
    void restoreRequest();

    void throttle(ContractCallRequest request);
}
