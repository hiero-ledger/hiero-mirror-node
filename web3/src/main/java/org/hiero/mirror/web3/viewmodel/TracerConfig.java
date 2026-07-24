// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import lombok.Builder;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.TracerType;
import org.springframework.validation.annotation.Validated;

@Builder(toBuilder = true)
@Validated
public record TracerConfig(
        boolean code,
        @JsonProperty("diff") boolean diff,
        boolean memory,
        boolean onlyTopCall,
        boolean stack,
        boolean storage,
        Duration timeout,
        TracerType tracerType) {

    public boolean isCode() {
        return code;
    }

    public boolean isDiff() {
        return diff;
    }

    public boolean isMemory() {
        return memory;
    }

    public boolean isOnlyTopCall() {
        return onlyTopCall;
    }

    public boolean isStack() {
        return stack;
    }

    public boolean isStorage() {
        return storage;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public TracerType getTracerType() {
        return tracerType;
    }
}
