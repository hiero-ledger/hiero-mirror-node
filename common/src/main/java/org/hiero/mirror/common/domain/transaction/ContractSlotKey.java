// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HookId;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * Unified key for both contract storage and lambda (hook) storage.
 * For regular contract storage: contractId is set, hookId is null
 * For lambda/hook storage: contractId is null, hookId is set
 */
@Builder(toBuilder = true)
public record ContractSlotKey(ContractSlotId slotId, ByteString key) {

    /**
     * Helper method to get contractId from slotId (may be null for hook storage)
     */
    public @Nullable ContractID contractId() {
        return slotId != null ? slotId.contractId() : null;
    }

    /**
     * Helper method to get hookId from slotId (may be null for contract storage)
     */
    public @Nullable HookId hookId() {
        return slotId != null ? slotId.hookId() : null;
    }
}
