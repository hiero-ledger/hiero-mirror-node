// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import org.hiero.mirror.web3.convert.BlockTypeSerializer;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimulateRequest {

    public static final int MAX_CALLS = 16;

    @JsonSerialize(using = BlockTypeSerializer.class)
    @JsonSetter(nulls = Nulls.SKIP)
    @NotNull
    private BlockType block = BlockType.LATEST;

    @JsonProperty("block_state_calls")
    @NotNull
    @Size(max = MAX_CALLS)
    private List<@NotNull @Valid SimulateBlockStateCall> blockStateCalls = List.of();

    @JsonProperty("trace_transfers")
    private boolean traceTransfers;

    public long totalGas() {
        long totalGas = 0L;
        for (final var blockCall : blockStateCalls) {
            if (blockCall != null && blockCall.getCalls() != null) {
                for (final var call : blockCall.getCalls()) {
                    totalGas += call.getGas();
                }
            }
        }
        return totalGas;
    }

    // Zero calls otherwise reaches the throttle bucket as a 0-token request, which bucket4j rejects with its own
    // exception.
    @AssertTrue(message = "at least one call is required across block_state_calls")
    private boolean hasAtLeastOneCall() {
        return blockStateCalls == null || totalCallCount() >= 1;
    }

    // Null-safe: Bean Validation still runs this against an explicit JSON null, and an NPE here turns a 400 into a 500.
    @AssertTrue(message = "total number of calls across block_state_calls must not exceed " + MAX_CALLS)
    private boolean hasValidCallCount() {
        return blockStateCalls == null || totalCallCount() <= MAX_CALLS;
    }

    private int totalCallCount() {
        int totalCallCount = 0;
        for (final var blockCall : blockStateCalls) {
            if (blockCall != null && blockCall.getCalls() != null) {
                totalCallCount += blockCall.getCalls().size();
            }
        }
        return totalCallCount;
    }
}
