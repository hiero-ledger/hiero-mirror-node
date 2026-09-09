// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service.model;

import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.Nullable;

@Value
@RequiredArgsConstructor
public class TraceRequest {

    @Valid
    ContractExecutionParameters contractExecutionParameters;

    boolean onlyTopCall;

    @Nullable
    Duration timeout;
}
