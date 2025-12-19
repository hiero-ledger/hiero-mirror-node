// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hedera.hapi.node.hooks.legacy.LambdaStorageUpdate;
import com.hedera.services.stream.proto.StorageChange;
import java.util.List;
import org.hiero.mirror.common.domain.entity.EntityId;

public interface EvmHookStorageHandler {
    void processStorageUpdates(
            long consensusTimestamp, long hookId, EntityId ownerId, List<LambdaStorageUpdate> storageUpdates);

    void processStorageUpdatesForSidecar(
            long consensusTimestamp, long hookId, long ownerId, List<StorageChange> storageUpdates);
}
