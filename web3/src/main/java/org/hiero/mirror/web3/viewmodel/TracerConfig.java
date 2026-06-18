// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.Builder;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.TracerType;

@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TracerConfig(
        boolean code,
        @JsonProperty("diff") boolean diff,
        boolean memory,
        boolean onlyTopCall,
        boolean stack,
        boolean storage,
        Instant timeout,
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

    public Instant getTimeout() {
        return timeout;
    }

    public TracerType getTracerType() {
        return tracerType;
    }
}
