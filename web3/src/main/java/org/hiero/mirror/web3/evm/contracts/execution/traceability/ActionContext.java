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
import org.hiero.mirror.rest.model.ActionResponse.TypeEnum;
import org.hiero.mirror.web3.viewmodel.TracerConfig;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class ActionContext {

    /**
     * Maximum call depth to track. Matches the EVM call-depth cap.
     */
    public static final int MAX_DEPTH = 1024;

    /**
     * Maximum number of total actions (root + nested) to collect. Prevents OOM from complex call graphs.
     */
    public static final int MAX_ACTIONS = 10_000;

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

    private long gasRemaining;

    private boolean timedOut;

    private boolean truncated;

    private TracerConfig tracerConfig;

    /**
     * Records a new action at the given call depth. Depth {@code 0} actions are roots; deeper actions are appended to
     * the parent action's {@code calls} list. Actions beyond {@link #MAX_ACTIONS} or at depth &gt;
     * {@link #MAX_DEPTH} are dropped and a truncation marker is recorded.
     */
    public void addAction(final ActionResponse actionResponse, final int depth) {
        if (totalActionCount >= MAX_ACTIONS || depth < 0 || depth > MAX_DEPTH) {
            markTruncated();
            return;
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
        getActionsByDepth(depth).add(actionResponse);
    }

    /**
     * Updates the current action at {@code depth} with the finalized frame result. The action stays in
     * {@link #actionsByDepth}; a later sibling at the same depth is appended after this frame.
     */
    public void finalizeAction(
            final int depth, final String error, final String gasUsed, final String output, final String revertReason) {
        if (depth < 0 || depth > MAX_DEPTH) {
            return;
        }
        final var actions = actionsByDepth[depth];
        if (actions == null || actions.isEmpty()) {
            return;
        }
        actions.getLast().error(error).gasUsed(gasUsed).output(output).revertReason(revertReason);
    }

    /**
     * All recorded actions, ordered by depth (all depth-0 first, then depth-1, etc.).
     */
    public List<ActionResponse> getActions() {
        final var result = new ArrayList<ActionResponse>();
        for (final var actions : actionsByDepth) {
            if (actions != null) {
                result.addAll(actions);
            }
        }
        return result;
    }

    /**
     * Returns all actions recorded at the specified depth, in chronological order.
     *
     * @param depth Call depth (0 = root, {@link #MAX_DEPTH} = max)
     * @return List of actions at that depth, or empty list if none
     */
    public List<ActionResponse> getActionsByDepth(final int depth) {
        if (depth < 0 || depth > MAX_DEPTH) {
            return new ArrayList<>();
        }
        final var actions = actionsByDepth[depth];
        if (actions == null) {
            actionsByDepth[depth] = new ArrayList<>();
        }
        return actionsByDepth[depth];
    }

    private void markTruncated() {
        if (truncated) {
            return;
        }
        truncated = true;
        getActionsByDepth(0).add(new ActionResponse().error(TRUNCATED_ERROR).type(TypeEnum.UNKNOWN));
    }
}
