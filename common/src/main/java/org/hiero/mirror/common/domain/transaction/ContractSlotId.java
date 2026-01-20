// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HookId;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * Identifier for either contract storage or lambda (hook) storage.
 * For regular contract storage: contractId is set, hookId is null
 * For lambda/hook storage: contractId is null, hookId is set
 */
@Builder(toBuilder = true)
public record ContractSlotId(
        @Nullable ContractID contractId, @Nullable HookId hookId) {
    public ContractSlotId {
        if ((contractId == null) == (hookId == null)) {
            throw new IllegalArgumentException("Exactly one of contractId or hookId must be set");
        }
    }
}
