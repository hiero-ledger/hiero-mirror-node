// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import lombok.Builder;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.TracerType;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.validation.annotation.Validated;

@Builder(toBuilder = true)
@Validated
public record TracerConfig(
        boolean code,
        boolean diff,
        boolean memory,
        @JsonAlias("only_top_call") boolean onlyTopCall,
        boolean stack,
        boolean storage,
        @Nullable String timeout,
        @JsonProperty("tracer") @Nullable TracerType tracerType) {

    public TracerType effectiveTracerType() {
        return tracerType == null ? TracerType.ACTION : tracerType;
    }

    /**
     * Parses timeout strings ({@code 30s}, {@code PT30S}). Returns {@code null} when unset. Throws
     * {@link IllegalArgumentException} when the value is not a duration.
     */
    public @Nullable Duration parsedTimeout() {
        if (timeout == null || timeout.isBlank()) {
            return null;
        }
        return DurationStyle.detectAndParse(timeout.trim());
    }

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
}
