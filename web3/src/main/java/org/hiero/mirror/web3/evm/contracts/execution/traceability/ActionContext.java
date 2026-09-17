// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hiero.mirror.rest.model.ActionResponse;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@NullMarked
public class ActionContext {

    /**
     * Maximum call depth to track. Matches the EVM call-depth cap (Besu depths {@code 0..1023}).
     */
    public static final int MAX_DEPTH = 1024;

    /**
     * Maximum number of total actions (root + nested) to collect. Prevents OOM from complex call graphs.
     */
    public static final int MAX_ACTIONS = 10_000;

    /**
     * Check the execution deadline every N opcodes while a frame is {@code CODE_EXECUTING}, so a 15M-gas loop cannot
     * ignore {@code api.actions.request.timeout}. Must be a power of two.
     */
    public static final int DEADLINE_CHECK_INTERVAL = 64;

    static final String TRUNCATED_ERROR = "Trace truncated after reaching the configured limit";

    /**
     * All actions organized by depth. Index {@code d} is every action at that depth in chronological order. The last
     * entry at a depth is the current frame; roots are index {@code 0}.
     */
    @Builder.Default
    @EqualsAndHashCode.Exclude
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    private List<ActionResponse>[] actionsByDepth = new List[MAX_DEPTH];

    @Builder.Default
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private int totalActionCount = 0;

    @Builder.Default
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private int executedOpcodes = 0;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private @Nullable ActionResponse lastAction;

    private boolean timedOut;

    private boolean truncated;

    /**
     * Include only the root message frame in the response.
     */
    private boolean onlyTopCall;

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
        getActionsByDepth(depth).add(actionResponse);
    }

    /**
     * {@code true} every {@link #DEADLINE_CHECK_INTERVAL} opcodes so the tracer can halt a tight loop.
     */
    public boolean shouldCheckDeadline() {
        return (++executedOpcodes & (DEADLINE_CHECK_INTERVAL - 1)) == 0;
    }

    /**
     * Updates the current action at {@code depth} with the finalized frame result. The action stays in
     * {@link #actionsByDepth}; a later sibling at the same depth is appended after this frame.
     */
    public void finalizeAction(
            final int depth,
            final @Nullable String error,
            final String gasUsed,
            final String output,
            final @Nullable String revertReason) {
        final var action = getCurrentAction(depth);
        if (action == null) {
            return;
        }
        if (TRUNCATED_ERROR.equals(action.getError())) {
            action.gasUsed(gasUsed).output(output).revertReason(revertReason);
            return;
        }
        action.error(error).gasUsed(gasUsed).output(output).revertReason(revertReason);
    }

    /**
     * Root actions only. Nested frames are already attached under each parent's {@code calls} list.
     */
    public List<ActionResponse> getActions() {
        final var roots = actionsByDepth[0];
        return roots == null ? List.of() : roots;
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

    private void markTruncated() {
        if (truncated) {
            return;
        }
        truncated = true;
        if (lastAction != null) {
            lastAction.error(TRUNCATED_ERROR);
        }
    }
}
