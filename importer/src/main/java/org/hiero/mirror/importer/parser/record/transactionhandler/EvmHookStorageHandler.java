// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hedera.hapi.node.hooks.legacy.LambdaStorageUpdate;
import com.hedera.services.stream.proto.StorageChange;
import java.util.List;

public interface EvmHookStorageHandler {

    void processStorageUpdates(
            long consensusTimestamp, long hookId, long ownerId, List<LambdaStorageUpdate> storageUpdates);

    void processStorageUpdatesForSidecar(
            long consensusTimestamp, long hookId, long ownerId, List<StorageChange> storageUpdates);
}
