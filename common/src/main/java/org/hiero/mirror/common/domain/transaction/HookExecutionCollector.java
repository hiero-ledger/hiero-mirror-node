// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.hederahashgraph.api.proto.java.HookCall;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.domain.hook.AbstractHook;

/**
 * Collects hook IDs for different execution phases and provides methods to build the final execution queue.
 * <p>
 * This record encapsulates the three types of hook executions as specified in HIP-1195: 1. allowExecHookIds - PreTx
 * hooks (HBAR, Token, NFT transfers) 2. allowPreExecHookIds - Pre hooks from PrePostTx (HBAR, Token, NFT transfers) 3.
 * allowPostExecHookIds - Post hooks from PrePostTx (HBAR, Token, NFT transfers)
 * <p>
 * The execution order is: allowExecHookIds → allowPreExecHookIds → allowPostExecHookIds
 */
public record HookExecutionCollector(
        List<AbstractHook.Id> allowExecHookIds,
        List<AbstractHook.Id> allowPreExecHookIds,
        List<AbstractHook.Id> allowPostExecHookIds) {

    /**
     * Creates a new HookExecutionCollector with empty lists.
     */
    public static HookExecutionCollector create() {
        return new HookExecutionCollector(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    /**
     * Adds a hook ID to the allowExecHookIds list (PreTx hooks).
     *
     * @param hookCall the hook call to add
     * @param ownerId  the owner ID for the hook
     * @return this collector for method chaining
     */
    public HookExecutionCollector addAllowExecHook(HookCall hookCall, long ownerId) {
        if (hookCall.hasHookId()) {
            allowExecHookIds.add(new AbstractHook.Id(hookCall.getHookId(), ownerId));
        }
        return this;
    }

    /**
     * Adds a hook ID to both allowPreExecHookIds and allowPostExecHookIds lists (PrePostTx hooks).
     *
     * @param hookCall the hook to add
     * @param ownerId  the owner ID for the hook
     * @return this collector for method chaining
     */
    public HookExecutionCollector addPrePostExecHook(HookCall hookCall, long ownerId) {
        if (hookCall.hasHookId()) {
            var hookIdObj = new AbstractHook.Id(hookCall.getHookId(), ownerId);
            allowPreExecHookIds.add(hookIdObj);
            allowPostExecHookIds.add(hookIdObj);
        }
        return this;
    }

    /**
     * Builds the final hook execution queue in the correct order: allowExecHookIds → allowPreExecHookIds →
     * allowPostExecHookIds
     *
     * @return ArrayDeque containing all hook IDs in execution order
     */
    public ArrayDeque<AbstractHook.Id> buildExecutionQueue() {
        var hookExecutionQueue = new ArrayDeque<AbstractHook.Id>();
        hookExecutionQueue.addAll(allowExecHookIds);
        hookExecutionQueue.addAll(allowPreExecHookIds);
        hookExecutionQueue.addAll(allowPostExecHookIds);
        return hookExecutionQueue;
    }
}
