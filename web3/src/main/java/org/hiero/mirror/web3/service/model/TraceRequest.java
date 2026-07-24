// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class TraceRequest {

    @Valid
    ContractExecutionParameters contractExecutionParameters;

    boolean onlyTopCall;
}
