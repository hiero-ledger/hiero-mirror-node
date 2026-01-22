// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HookId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

/**
 * Identifier for either contract storage or lambda (hook) storage. For regular contract storage: contractId is set,
 * hookId is null For lambda/hook storage: contractId is null, hookId is set
 *
 * <p><strong>Note:</strong> Use the static factory method {@link #of(ContractID, HookId)} to create instances.
 * Direct construction is not allowed.
 */
@EqualsAndHashCode
@Getter
@ToString
public final class ContractSlotId {

    private static final long HOOK_SYSTEM_CONTRACT_NUM = 365L;

    @Nullable
    private final ContractID contractId;

    @Nullable
    private final HookId hookId;

    private ContractSlotId(@Nullable ContractID contractId, @Nullable HookId hookId) {
        this.contractId = contractId;
        this.hookId = hookId;
    }

    /**
     * Creates a ContractSlotId based on the contract ID and executed hook ID. The logic determines whether this is
     * contract storage or hook storage:
     * <ul>
     *   <li>If contractId is the hook system contract (365) AND executedHookId is non-null: hook storage</li>
     *   <li>If contractId is NOT the hook system contract: contract storage (hook is ignored)</li>
     *   <li>If contractId is the hook system contract (365) but executedHookId is null: returns null (invalid state)</li>
     * </ul>
     *
     * @param contractId     the contract ID
     * @param executedHookId the executed hook ID (may be null)
     * @return ContractSlotId for either contract or hook storage, or null if the state is invalid (hook system contract
     * without executed hook)
     */
    public static @Nullable ContractSlotId of(@Nullable ContractID contractId, @Nullable HookId executedHookId) {
        // If contractId is null but hookId is provided, create hook storage
        if (contractId == null && executedHookId != null) {
            return new ContractSlotId(null, executedHookId);
        }

        // Invalid: contractId is null and executedHookId is also null (since we didn't return above)
        if (contractId == null) {
            throw new IllegalArgumentException("Exactly one of contractId or hookId must be set");
        }

        // Invalid state: hook system contract without executed hook
        if (contractId.getContractNum() == HOOK_SYSTEM_CONTRACT_NUM && executedHookId == null) {
            return null;
        }

        // Hook system contract with executed hook: create hook storage
        if (contractId.getContractNum() == HOOK_SYSTEM_CONTRACT_NUM && executedHookId != null) {
            return new ContractSlotId(null, executedHookId);
        }

        // Regular contract (not 365): create contract storage, ignore hook
        return new ContractSlotId(contractId, null);
    }
}
