// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class TraceRequest {

    ContractExecutionParameters contractExecutionParameters;
    boolean onlyTopCall;
}
