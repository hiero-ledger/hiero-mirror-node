// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.domain.hook.AbstractHook;

/**
 * Collects hook IDs for different execution phases and provides methods to build the final execution queue.
 *
 * This record encapsulates the three types of hook executions as specified in HIP-1195:
 * 1. allowExecHookIds - PreTx hooks (HBAR, Token, NFT transfers)
 * 2. allowPreExecHookIds - Pre hooks from PrePostTx (HBAR, Token, NFT transfers)
 * 3. allowPostExecHookIds - Post hooks from PrePostTx (HBAR, Token, NFT transfers)
 *
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
     * @param hookId the hook ID to add
     * @param ownerId the owner ID for the hook
     * @return this collector for method chaining
     */
    public HookExecutionCollector addAllowExecHook(long hookId, long ownerId) {
        allowExecHookIds.add(new AbstractHook.Id(hookId, ownerId));
        return this;
    }

    /**
     * Adds a hook ID to both allowPreExecHookIds and allowPostExecHookIds lists (PrePostTx hooks).
     *
     * @param hookId the hook ID to add
     * @param ownerId the owner ID for the hook
     * @return this collector for method chaining
     */
    public HookExecutionCollector addPrePostExecHook(long hookId, long ownerId) {
        var hookIdObj = new AbstractHook.Id(hookId, ownerId);
        allowPreExecHookIds.add(hookIdObj);
        allowPostExecHookIds.add(hookIdObj);
        return this;
    }

    /**
     * Builds the final hook execution queue in the correct order:
     * allowExecHookIds → allowPreExecHookIds → allowPostExecHookIds
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

    /**
     * Returns true if any of the hook lists contain hooks.
     *
     * @return true if there are any hooks to execute, false otherwise
     */
    public boolean hasHooks() {
        return !allowExecHookIds.isEmpty() || !allowPreExecHookIds.isEmpty() || !allowPostExecHookIds.isEmpty();
    }

    /**
     * Returns the total number of hooks across all execution phases.
     *
     * @return total number of hooks
     */
    public int totalHookCount() {
        return allowExecHookIds.size() + allowPreExecHookIds.size() + allowPostExecHookIds.size();
    }
}
