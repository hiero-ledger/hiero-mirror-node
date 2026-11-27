// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import org.hiero.mirror.web3.convert.BlockTypeSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

@Data
public class SimulateRequest {

    public static final int MAX_BLOCK_STATE_CALLS = 1;

    @JsonSerialize(using = BlockTypeSerializer.class)
    @JsonSetter(nulls = Nulls.SKIP)
    @NotNull
    private BlockType block = BlockType.LATEST;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @Size(min = 1, max = MAX_BLOCK_STATE_CALLS)
    @Valid
    private List<@NotNull SimulateBlockStateCall> blockStateCalls = List.of();

    private boolean traceTransfers;

    public long totalGas() {
        long totalGas = 0L;
        for (final var blockCall : blockStateCalls) {
            for (final var call : blockCall.getCalls()) {
                totalGas += call.getGas();
            }
        }
        return totalGas;
    }
}
