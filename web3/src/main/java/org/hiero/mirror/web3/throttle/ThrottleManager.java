// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.throttle;

import org.hiero.mirror.web3.viewmodel.ContractCallRequest;

public interface ThrottleManager {

    void throttle(ContractCallRequest request);

    void throttleOpcodeRequest();

    void restore(long gas);
}
