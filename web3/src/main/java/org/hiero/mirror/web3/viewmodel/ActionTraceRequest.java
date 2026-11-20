// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.MAX_TRACE_TRANSACTIONS;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.hiero.mirror.web3.convert.BlockTypeSerializer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One {@code debug_traceCallMany} request. A single bundle with one transaction is {@code debug_traceCall}.
 * {@code block}, {@code state_overrides}, {@code timeout}, and {@code transaction_index} apply to every bundle.
 * {@code state_overrides} is one entry per account. When {@code only_top_call} is true, each transaction returns only
 * its root call and omits nested actions. State changes from earlier bundles stay visible to later bundles.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NullMarked
public class ActionTraceRequest {

    /**
     * Request body limit enforced by Jackson. It allows full calldata on every transaction plus 256 KiB for the rest of
     * the JSON, so calldata and state overrides together cannot exceed it.
     */
    public static final long MAX_DOCUMENT_LENGTH =
            MAX_TRACE_TRANSACTIONS * ContractCallRequest.DATA_MAX_LENGTH + 256 * 1024;

    public static final long MAX_JSON_TOKENS = 100_000;

    @JsonSerialize(using = BlockTypeSerializer.class)
    @JsonSetter(nulls = Nulls.SKIP)
    @NotNull
    private BlockType block = BlockType.LATEST;

    @NotEmpty
    @NotNull
    @Valid
    private List<@NotNull @Valid ActionTraceBundle> bundles = new ArrayList<>();

    private boolean onlyTopCall;

    @JsonSetter(nulls = Nulls.SKIP)
    @NotNull
    @Size(max = 10)
    private List<@NotNull @Valid StateOverride> stateOverrides = new ArrayList<>();

    @Nullable
    private String timeout;

    /**
     * Position of an EVM transaction inside {@link #block}, ordered by consensus timestamp. {@code 0} starts the
     * simulation at the beginning of the block, before its first EVM transaction. A positive value {@code N} reads
     * state one nanosecond after the consensus timestamp of the Nth EVM transaction. {@code -1} and {@code null} start
     * the simulation after every transaction in the block. It has no effect when {@link #block} is {@code latest}. The
     * timestamp applies to the whole request. Values less than {@code -1} are rejected.
     */
    @Min(-1)
    @Nullable
    private Integer transactionIndex;

    @AssertTrue(message = "transactions field size must be between 1 and " + MAX_TRACE_TRANSACTIONS)
    private boolean hasTransactionLimit() {
        if (bundles == null) {
            return true;
        }
        int count = 0;
        for (final var bundle : bundles) {
            if (bundle == null || bundle.getTransactions() == null) {
                continue;
            }
            count += bundle.getTransactions().size();
        }
        return count <= MAX_TRACE_TRANSACTIONS;
    }
}
