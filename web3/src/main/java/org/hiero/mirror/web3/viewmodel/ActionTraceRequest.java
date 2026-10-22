// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.hiero.mirror.web3.convert.BlockTypeSerializer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One {@code debug_traceCallMany} request. A single bundle with one transaction is {@code debug_traceCall}.
 * {@code block}, {@code state_override}, {@code timeout}, and {@code transaction_index} apply to every bundle.
 * {@code only_top_call} is supported only for {@code debug_traceCall}. State changes from earlier bundles stay visible
 * to later bundles. Each bundle applies its own {@code block_override}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NullMarked
public class ActionTraceRequest {

    @JsonSerialize(using = BlockTypeSerializer.class)
    @JsonSetter(nulls = Nulls.SKIP)
    @NotNull
    private BlockType block = BlockType.LATEST;

    @NotEmpty
    @NotNull
    @Valid
    private List<@Valid ActionTraceBundle> bundles = new ArrayList<>();

    @JsonProperty("only_top_call")
    private boolean onlyTopCall;

    @JsonProperty("state_override")
    @Nullable
    @Valid
    private StateOverride stateOverride;

    @Nullable
    private String timeout;

    /**
     * Position of a transaction inside {@link #block}, ordered by consensus timestamp. {@code 0} starts the simulation
     * at the beginning of the block, before its first transaction. A positive value reads state at that transaction's
     * consensus timestamp. {@code -1} and {@code null} start the simulation after every transaction in the block. The
     * timestamp applies to the whole request. Values less than {@code -1} are rejected.
     */
    @JsonProperty("transaction_index")
    @Nullable
    private Integer transactionIndex;
}
