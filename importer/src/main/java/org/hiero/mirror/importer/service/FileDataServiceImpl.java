// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.service;

import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_FILE_DATA;
import static org.hiero.mirror.importer.config.CacheConfiguration.CACHE_NAME;

import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.RecordFileParsedEvent;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.repository.FileDataRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.CollectionUtils;

@Named
@Order
final class FileDataServiceImpl implements FileDataService, EntityListener {

    private final Cache cache;
    private final FileDataRepository fileDataRepository;
    private final Map<EntityId, List<FileData>> uncommited = new HashMap<>();

    FileDataServiceImpl(@Qualifier(CACHE_FILE_DATA) CacheManager cacheManager, FileDataRepository fileDataRepository) {
        this.cache = cacheManager.getCache(CACHE_NAME);
        this.fileDataRepository = fileDataRepository;
    }

    @Override
    public byte[] get(EntityId fileId) {
        if (EntityId.isEmpty(fileId) || DomainUtils.isSystemEntity(fileId)) {
            return null;
        }

        var uncommitedContents = uncommited.get(fileId);
        if (isFirstChunkNewContent(uncommitedContents)) {
            return combine(null, uncommitedContents);
        }

        byte[] cached = cache.get(fileId, byte[].class);
        if (cached != null) {
            return combine(cached, uncommitedContents);
        }

        return fileDataRepository
                .getFileAtTimestamp(fileId.getId(), Long.MAX_VALUE)
                .map(dbContent -> {
                    byte[] commited = dbContent.getFileData();
                    // safe to put already committed data into the cache
                    cache.put(fileId, commited);
                    return combine(commited, uncommitedContents);
                })
                .orElse(null);
    }

    @Override
    public void onFileData(@Nonnull FileData fileData) {
        if (DomainUtils.isSystemEntity(fileData.getEntityId())) {
            return;
        }

        var entityId = fileData.getEntityId();
        if (isNewContent(fileData)) {
            uncommited.put(entityId, Lists.newArrayList(fileData));
        } else if (fileData.getDataSize() > 0) {
            uncommited.computeIfAbsent(entityId, k -> new ArrayList<>()).add(fileData);
        }
    }

    @TransactionalEventListener(RecordFileParsedEvent.class)
    public void onRecordFileParsed() {
        // only commit the changes to the cache when the record file has been parsed successfully, otherwise because
        // the cache is bounded, updating the cache in onFileData may evict entries and then cause data loss
        for (var fileId : uncommited.keySet()) {
            var contents = uncommited.get(fileId);
            if (isFirstChunkNewContent(contents)) {
                cache.put(fileId, combine(null, contents));
            } else {
                byte[] cached = cache.get(fileId, byte[].class);
                if (cached != null) {
                    cache.put(fileId, combine(cached, contents));
                }
            }
        }

        uncommited.clear();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK, value = RecordFileParsedEvent.class)
    public void onRecordFileParsingRolledBack() {
        // note: don't use TransactionPhase.AFTER_COMPLETION, since the handler may get called before the AFTER_COMMIT
        // handler
        uncommited.clear();
    }

    private static byte[] combine(byte[] first, List<FileData> remaining) {
        if (CollectionUtils.isEmpty(remaining)) {
            return first;
        }

        if (first == null && remaining.size() == 1) {
            return remaining.getFirst().getFileData();
        }

        int size = first != null ? first.length : 0;
        for (var fileData : remaining) {
            size += fileData.getDataSize();
        }

        if (size == 0) {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }

        byte[] combined = new byte[size];
        int offset = 0;
        if (first != null) {
            System.arraycopy(first, 0, combined, 0, first.length);
            offset = first.length;
        }

        for (var fileData : remaining) {
            if (fileData.getDataSize() == 0) {
                continue;
            }

            System.arraycopy(fileData.getFileData(), 0, combined, offset, fileData.getDataSize());
            offset += fileData.getDataSize();
        }

        return combined;
    }

    private static boolean isFirstChunkNewContent(List<FileData> contents) {
        return !CollectionUtils.isEmpty(contents) && isNewContent(contents.getFirst());
    }

    private static boolean isNewContent(FileData fileData) {
        var transactionType = fileData.getTransactionType();
        return transactionType != null
                && (transactionType == TransactionType.FILECREATE.getProtoId()
                        || (transactionType == TransactionType.FILEUPDATE.getProtoId() && fileData.getDataSize() > 0));
    }
}
