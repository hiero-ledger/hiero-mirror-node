// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import java.util.ArrayList;
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
     * All actions organized by depth for the current request body. Index {@code d} is every action at that depth in
     * chronological order. The last entry at a depth is the current frame; the current body's root is index {@code 0}.
     */
    private List<ActionResponse>[] actionsByDepth = emptyActionsByDepth();

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
     * Records a new action at the given call depth. Depth {@code 0} actions are roots; deeper actions are appended to
     * the parent action's {@code calls} list. Actions beyond {@link #MAX_ACTIONS} or at depth &gt;=
     * {@link #MAX_DEPTH} are dropped and a truncation marker is recorded.
     */
    public void addAction(final ActionResponse actionResponse, final int depth) {
        if (truncated || totalActionCount >= MAX_ACTIONS || depth < 0 || depth >= MAX_DEPTH) {
            markTruncated();
            return;
        }
        if (!charge(textLength(actionResponse.getInput()))) {
            return;
        }
        if (actionResponse.getCalls() == null) {
            actionResponse.setCalls(new ArrayList<>());
        }
        if (depth > 0) {
            final var parents = actionsByDepth[depth - 1];
            if (parents == null || parents.isEmpty()) {
                markTruncated();
                return;
            }
            parents.getLast().addCallsItem(actionResponse);
        }

        totalActionCount++;
        lastAction = actionResponse;
        if (depth == 0) {
            actionResponses.add(actionResponse);
        }
        getActionsByDepth(depth).add(actionResponse);
    }

    /**
     * Starts a new request body. Resets the current call-tree index and truncation flag so the next depth-0 action can
     * be recorded. Previously completed bodies, including a truncation error already written on the previous
     * transaction, are kept. {@link #totalActionCount}, {@link #retainedBytes}, and {@link #executedOpcodes} stay
     * cumulative so later transactions share one action ceiling, one byte budget, and the deadline check does not skip
     * a window.
     */
    public void beginCall() {
        actionsByDepth = emptyActionsByDepth();
        lastAction = null;
        truncated = false;
    }

    /**
     * Updates the current action at {@code depth} with the finalized frame result. The action stays in
     * {@link #actionsByDepth}; a later sibling at the same depth is appended after this frame.
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
     * Returns all actions recorded at the specified depth, in chronological order.
     *
     * @param depth Call depth (0 = root, {@link #MAX_DEPTH} exclusive)
     * @return List of actions at that depth, or empty list if none
     */
    public List<ActionResponse> getActionsByDepth(final int depth) {
        if (depth < 0 || depth >= MAX_DEPTH) {
            return List.of();
        }
        if (actionsByDepth[depth] == null) {
            actionsByDepth[depth] = new ArrayList<>();
        }
        return actionsByDepth[depth];
    }

    /**
     * Returns the current (last) action at {@code depth}, or {@code null} if none has been recorded.
     */
    public @Nullable ActionResponse getCurrentAction(final int depth) {
        if (depth < 0 || depth >= MAX_DEPTH) {
            return null;
        }
        final var actions = actionsByDepth[depth];
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        return actions.getLast();
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<ActionResponse>[] emptyActionsByDepth() {
        return new List[MAX_DEPTH];
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
        final var marker = new ActionResponse().error(TRUNCATED_ERROR).calls(new ArrayList<>());
        actionResponses.add(marker);
        lastAction = marker;
        getActionsByDepth(0).add(marker);
    }

    private static int textLength(final @Nullable String value) {
        return value == null ? 0 : value.length();
    }
}
