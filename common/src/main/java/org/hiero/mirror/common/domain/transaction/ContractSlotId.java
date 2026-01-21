// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.EvmTransactionResult;
import com.hederahashgraph.api.proto.java.HookId;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * Identifier for either contract storage or lambda (hook) storage. For regular contract storage: contractId is set,
 * hookId is null For lambda/hook storage: contractId is null, hookId is set
 */
@Builder(toBuilder = true)
public record ContractSlotId(
        @Nullable ContractID contractId, @Nullable HookId hookId) {

    private static final long HOOK_SYSTEM_CONTRACT_NUM = 365L;

    public ContractSlotId {
        if ((contractId == null) == (hookId == null)) {
            throw new IllegalArgumentException("Exactly one of contractId or hookId must be set");
        }
    }

    /**
     * Creates a ContractSlotId based on the contract ID and EVM transaction result. This method extracts the hook ID
     * from the EVM transaction result if present.
     *
     * @param contractId           the contract ID
     * @param evmTransactionResult the EVM transaction result (may be null)
     * @return ContractSlotId for either contract or hook storage
     */
    public static ContractSlotId of(
            @Nullable ContractID contractId, @Nullable EvmTransactionResult evmTransactionResult) {
        HookId hookId = null;
        if (evmTransactionResult != null && evmTransactionResult.hasExecutedHookId()) {
            hookId = evmTransactionResult.getExecutedHookId();
            if (contractId.getContractNum() == HOOK_SYSTEM_CONTRACT_NUM) {
                contractId = null;
            }
        }
        return new ContractSlotId(contractId, hookId);
    }
}
