// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.web3.viewmodel.TracerConfig;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class ActionContext {

    @Builder.Default
    private List<ActionResponse> actions = new ArrayList<>();

    /**
     * Latest open {@link ActionResponse} at each call depth, used to attach nested calls to their parent.
     */
    @Builder.Default
    private Map<Integer, ActionResponse> actionsByDepth = new HashMap<>();

    private long gasRemaining;

    private TracerConfig tracerConfig;

    /**
     * Records a new action at the given call depth. Depth {@code 0} actions are roots; deeper actions are appended to
     * the parent action's {@code calls} list.
     */
    public void addAction(final ActionResponse actionResponse, final int depth) {
        if (depth <= 0) {
            actions.add(actionResponse);
        } else {
            final var parent = actionsByDepth.get(depth - 1);
            if (parent != null) {
                parent.addCallsItem(actionResponse);
            } else {
                actions.add(actionResponse);
            }
        }
        actionsByDepth.put(depth, actionResponse);
    }

    /**
     * Updates the open action at {@code depth} with the finalized frame result and closes that depth (and any deeper
     * depths) so subsequent sibling calls attach to the correct parent.
     */
    public void finalizeAction(final int depth, final ActionResponse finalized) {
        final var action = actionsByDepth.get(depth);
        if (action == null) {
            addAction(finalized, depth);
        } else {
            action.error(finalized.getError())
                    .gasUsed(finalized.getGasUsed())
                    .output(finalized.getOutput())
                    .revertReason(finalized.getRevertReason());
        }
        actionsByDepth.entrySet().removeIf(entry -> entry.getKey() >= depth);
    }
}
