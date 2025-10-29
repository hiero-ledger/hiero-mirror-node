// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.hedera.services.stream.proto.TransactionSidecarRecord;
import jakarta.inject.Named;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.hook.HookStorageChange;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
@CustomLog
@RequiredArgsConstructor
final class EVMHookStorageHandler {
    private final EntityListener entityListener;

    void processStorageUpdates(
            long consensusTimestamp, long hookId, long ownerId, List<TransactionSidecarRecord> sidecarRecords) {
        for (final var record : sidecarRecords) {
            if (!record.hasStateChanges()) {
                log.warn(
                        "Ignoring storage update as sidecar record without state changes at consensusTimestamp={} for owner={} hook={}",
                        consensusTimestamp,
                        ownerId,
                        hookId);
                continue;
            }
            final var stateChanges = record.getStateChanges();
            for (final var stateChange : stateChanges.getContractStateChangesList()) {
                for (final var storageChange : stateChange.getStorageChangesList()) {
                    final var hookStorageUpdate = HookStorageChange.builder()
                            .consensusTimestamp(consensusTimestamp)
                            .hookId(hookId)
                            .ownerId(ownerId)
                            .key(DomainUtils.toBytes(storageChange.getSlot()))
                            .valueRead(DomainUtils.toBytes(
                                    storageChange.getValueWritten().getValue()));

                    if (storageChange.hasValueWritten()) {
                        hookStorageUpdate.valueWritten(DomainUtils.toBytes(
                                storageChange.getValueWritten().getValue()));
                    }

                    entityListener.onHookStorageChange(hookStorageUpdate.build());
                }
            }
        }
    }
}
