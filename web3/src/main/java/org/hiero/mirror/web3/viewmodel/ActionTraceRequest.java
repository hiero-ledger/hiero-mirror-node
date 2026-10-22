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
     * the position of a transaction inside a {@link #block}, ordered by consensus timestamp. For example 5 loads the state
     * after the first five transactions. {@code -1} uses the block state directly. {@code null} means the field was
     * omitted and uses the block state directly.
     */
    @JsonProperty("transaction_index")
    @Nullable
    private Integer transactionIndex;
}
