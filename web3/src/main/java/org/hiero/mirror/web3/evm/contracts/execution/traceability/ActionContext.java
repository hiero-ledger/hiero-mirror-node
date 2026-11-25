// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.rest.model.ActionResponse;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class ActionContext {

    /**
     * Check the execution deadline every N opcodes while a frame is {@code CODE_EXECUTING}, so a 15M-gas loop cannot
     * ignore {@code api.actions.response.timeout}. Must be a power of two.
     */
    public static final int DEADLINE_CHECK_INTERVAL = 64;

    /**
     * Maximum number of total actions (root + nested) to collect for the whole request. Prevents OOM from complex call
     * graphs. Shared across every transaction; {@link #beginCall()} does not reset it.
     */
    public static final int MAX_ACTIONS = 10_000;

    /**
     * Maximum retained call depth. Each frame adds an object and calls array, so 40 frames fit Jackson's nesting
     * limit of 100. This does not limit EVM execution depth.
     */
    public static final int MAX_DEPTH = 40;

    /** Maximum retained {@code input}, {@code output}, and {@code revertReason} characters for one request. */
    public static final int MAX_RETAINED_BYTES = 4 * 1024 * 1024;

    static final String TRUNCATED_ERROR = "Trace truncated after reaching the configured limit";

    /**
     * One top-level {@link ActionResponse} per request body. Nested frames are attached under each body's
     * {@code calls} list.
     */
    private final List<ActionResponse> actionResponses = new ArrayList<>();

    /**
     * The most recent action at each call depth for the current request body. Index {@code d} is the current frame at
     * that depth, and index {@code d - 1} is its parent. The current body's root is index {@code 0}.
     */
    private final @Nullable ActionResponse[] currentByDepth = new ActionResponse[MAX_DEPTH];

    /** The gas of the action at the same index of {@link #currentByDepth}, kept as a number for the gas used. */
    private final long[] gasByDepth = new long[MAX_DEPTH];

    private int executedOpcodes;

    private @Nullable ActionResponse lastAction;

    /**
     * Include only the root message frame in the response.
     */
    @Getter
    @Setter
    private boolean onlyTopCall;

    private int retainedBytes;

    @Getter
    @Setter
    private boolean timedOut;

    private int totalActionCount;

    @Getter
    private boolean truncated;

    /**
     * Records a new action with {@code gas} at the given call depth. Depth {@code 0} actions are roots; deeper actions
     * are appended to the parent action's {@code calls} list. Actions beyond {@link #MAX_ACTIONS} or at depth &gt;=
     * {@link #MAX_DEPTH} are dropped and a truncation marker is recorded.
     */
    public void addAction(final ActionResponse actionResponse, final int depth, final long gas) {
        if (truncated || totalActionCount >= MAX_ACTIONS || depth < 0 || depth >= MAX_DEPTH) {
            markTruncated();
            return;
        }
        if (!charge(textLength(actionResponse.getInput()))) {
            return;
        }
        if (depth > 0) {
            final var parent = currentByDepth[depth - 1];
            if (parent == null) {
                markTruncated();
                return;
            }
            parent.addCallsItem(actionResponse);
        }

        totalActionCount++;
        lastAction = actionResponse;
        if (depth == 0) {
            actionResponses.add(actionResponse);
        }
        currentByDepth[depth] = actionResponse;
        gasByDepth[depth] = gas;
    }

    /**
     * Starts a new request body. Resets the current call-tree index and truncation flag so the next depth-0 action can
     * be recorded. Previously completed bodies, including a truncation error already written on the previous
     * transaction, are kept. {@link #totalActionCount}, {@link #retainedBytes}, and {@link #executedOpcodes} stay
     * cumulative so later transactions share one action ceiling, one byte budget, and the deadline check does not skip
     * a window.
     */
    public void beginCall() {
        Arrays.fill(currentByDepth, null);
        lastAction = null;
        truncated = false;
    }

    /**
     * Updates the current action at {@code depth} with the finalized frame result. A later sibling at the same depth
     * replaces it as the current action.
     */
    public void finalizeAction(
            final int depth,
            final @Nullable String error,
            final String gasUsed,
            final @Nullable String output,
            final @Nullable String revertReason) {
        final var action = getCurrentAction(depth);
        if (action == null) {
            return;
        }
        if (!charge(textLength(output) + textLength(revertReason))) {
            action.gasUsed(gasUsed);
            return;
        }
        if (TRUNCATED_ERROR.equals(action.getError())) {
            action.gasUsed(gasUsed).output(output).revertReason(revertReason);
            return;
        }
        action.error(error).gasUsed(gasUsed).output(output).revertReason(revertReason);
    }

    /**
     * Top-level action for each request body. Nested frames are already attached under each body's {@code calls} list.
     */
    public List<ActionResponse> getActions() {
        return actionResponses;
    }

    /**
     * Returns the current (last) action at {@code depth}, or {@code null} if none has been recorded.
     */
    public @Nullable ActionResponse getCurrentAction(final int depth) {
        if (depth < 0 || depth >= MAX_DEPTH) {
            return null;
        }
        return currentByDepth[depth];
    }

    /** Returns the gas of the current action at {@code depth}, or {@code 0} if none has been recorded. */
    public long getCurrentGas(final int depth) {
        if (depth < 0 || depth >= MAX_DEPTH) {
            return 0L;
        }
        return gasByDepth[depth];
    }

    public boolean hasActionAt(final int depth) {
        return getCurrentAction(depth) != null;
    }

    /**
     * Hex-encodes {@code bytes} when {@code 0x} plus the hex digits fit in the remaining retained-byte budget.
     * Otherwise marks the trace truncated and returns null without allocating the hex string.
     */
    public @Nullable String hexWithinBudget(final @Nullable Bytes bytes) {
        if (bytes == null) {
            return null;
        }
        final long encoded = 2L * bytes.size() + 2L;
        if (encoded > Integer.MAX_VALUE || (long) retainedBytes + encoded > MAX_RETAINED_BYTES) {
            markTruncated();
            return null;
        }
        return bytes.toHexString();
    }

    /**
     * {@code true} every {@link #DEADLINE_CHECK_INTERVAL} opcodes so the tracer can halt a tight loop.
     */
    public boolean shouldCheckDeadline() {
        return (++executedOpcodes & (DEADLINE_CHECK_INTERVAL - 1)) == 0;
    }

    private boolean charge(final int additional) {
        if (additional <= 0) {
            return true;
        }
        if ((long) retainedBytes + additional > MAX_RETAINED_BYTES) {
            markTruncated();
            return false;
        }
        retainedBytes += additional;
        return true;
    }

    private void markTruncated() {
        if (truncated) {
            return;
        }
        truncated = true;
        if (lastAction != null) {
            lastAction.error(TRUNCATED_ERROR);
            return;
        }
        // This transaction has no action yet. Do not rewrite the previous transaction's last action.
        final var marker = new ActionResponse().error(TRUNCATED_ERROR);
        actionResponses.add(marker);
        lastAction = marker;
        currentByDepth[0] = marker;
        gasByDepth[0] = 0L;
    }

    private static int textLength(final @Nullable String value) {
        return value == null ? 0 : value.length();
    }
}
