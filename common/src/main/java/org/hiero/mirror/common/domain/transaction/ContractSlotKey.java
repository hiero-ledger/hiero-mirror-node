// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static lombok.AccessLevel.PRIVATE;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HookId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.CustomLog;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 * Unified key for both contract storage and lambda (hook) storage.
 * For regular contract storage: contractId is set, hookId is null
 * For lambda/hook storage: contractId is null, hookId is set
 */
@Builder(toBuilder = true)
@AllArgsConstructor(access = PRIVATE)
@CustomLog
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode
@Value
public final class ContractSlotKey {
    private final ContractID contractId;
    private final HookId hookId;
    private final ByteString key;
}
