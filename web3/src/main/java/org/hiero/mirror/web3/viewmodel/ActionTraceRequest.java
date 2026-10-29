// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.viewmodel;

import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.MAX_TRACE_BUNDLES;
import static org.hiero.mirror.web3.viewmodel.ContractCallRequest.MAX_TRACE_TRANSACTIONS;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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
 * its root call and omits nested actions. State changes from earlier bundles stay visible to later bundles. Each
 * bundle applies its own {@code block_override}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NullMarked
public class ActionTraceRequest {

    public static final long MAX_DOCUMENT_LENGTH = 8 * 1024 * 1024;
    public static final long MAX_JSON_TOKENS = 100_000;
    public static final long MAX_INPUT_CHARACTERS = MAX_TRACE_TRANSACTIONS * ContractCallRequest.DATA_MAX_LENGTH;

    @JsonSerialize(using = BlockTypeSerializer.class)
    @JsonSetter(nulls = Nulls.SKIP)
    @NotNull
    private BlockType block = BlockType.LATEST;

    @NotEmpty
    @NotNull
    @Size(max = MAX_TRACE_BUNDLES)
    @Valid
    private List<@NotNull @Valid ActionTraceBundle> bundles = new ArrayList<>();

    @JsonProperty("only_top_call")
    private boolean onlyTopCall;

    @JsonProperty("state_overrides")
    @JsonSetter(nulls = Nulls.SKIP)
    @NotNull
    @Size(max = 10)
    private List<@NotNull @Valid StateOverride> stateOverrides = new ArrayList<>();

    @AssertTrue(message = "aggregate calldata and state overrides exceed the trace input limit")
    private boolean hasInputBudget() {
        long length = overrideLength(stateOverrides);
        if (bundles != null) {
            for (final var bundle : bundles) {
                if (bundle == null || bundle.getTransactions() == null) {
                    continue;
                }
                for (final var transaction : bundle.getTransactions()) {
                    if (transaction != null) {
                        length += textLength(transaction.getData()) + overrideLength(transaction.getStateOverrides());
                    }
                }
            }
        }
        return length <= MAX_INPUT_CHARACTERS;
    }

    private static long overrideLength(final List<StateOverride> overrides) {
        long length = 0;
        if (overrides == null) {
            return length;
        }
        for (final var override : overrides) {
            if (override == null) {
                continue;
            }
            length += textLength(override.getCode());
            length += storageLength(override.getState()) + storageLength(override.getStateDiff());
        }
        return length;
    }

    private static long storageLength(final List<StorageEntry> entries) {
        long length = 0;
        if (entries != null) {
            for (final var entry : entries) {
                if (entry != null) {
                    length += textLength(entry.getKey()) + textLength(entry.getValue());
                }
            }
        }
        return length;
    }

    private static int textLength(final @Nullable String text) {
        return text == null ? 0 : text.length();
    }

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
