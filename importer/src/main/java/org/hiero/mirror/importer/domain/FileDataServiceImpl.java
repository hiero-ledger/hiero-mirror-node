// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.domain;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.config.CacheProperties;
import org.hiero.mirror.importer.parser.record.RecordStreamFileListener;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.repository.FileDataRepository;
import org.springframework.core.annotation.Order;
import org.springframework.util.CollectionUtils;

@Named
@Order
@RequiredArgsConstructor
final class FileDataServiceImpl implements FileDataService, RecordStreamFileListener, EntityListener {

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Cache<EntityId, List<FileData>> fileContents = buildCache();

    private final CacheProperties cacheProperties;
    private final FileDataRepository fileDataRepository;
    private final AtomicLong lastConsensusTimestamp = new AtomicLong();

    @Override
    public byte[] get(long consensusTimestamp, EntityId fileId) {
        if (consensusTimestamp < lastConsensusTimestamp.get() || DomainUtils.isSystemEntity(fileId)) {
            return null;
        }

        var cache = getFileContents();
        var contents = cache.getIfPresent(fileId);
        if (isFirstChunkNewContent(contents)) {
            return contents.getLast().getConsensusTimestamp() <= consensusTimestamp ? combine(contents) : null;
        }

        return fileDataRepository
                .getFileAtTimestamp(fileId.getId(), consensusTimestamp)
                .map(dbContent -> {
                    if (contents == null) {
                        cache.put(fileId, Lists.newArrayList(dbContent));
                        return dbContent.getFileData();
                    } else {
                        contents.addFirst(dbContent);
                        return combine(contents);
                    }
                })
                .orElse(null);
    }

    @Override
    public void onFileData(@Nonnull FileData fileData) {
        if (DomainUtils.isSystemEntity(fileData.getEntityId())) {
            return;
        }

        var cache = getFileContents();
        if (isNewContent(fileData)) {
            cache.put(fileData.getEntityId(), Lists.newArrayList(fileData));
        } else if (fileData.getDataSize() > 0) {
            var contents = cache.get(fileData.getEntityId(), k -> new ArrayList<>());
            contents.add(fileData);
        }

        lastConsensusTimestamp.set(fileData.getConsensusTimestamp());
    }

    @Override
    public void onStart(@Nonnull RecordFile recordFile) {
        long consensusStart = recordFile.getConsensusStart();
        if (lastConsensusTimestamp.get() < consensusStart) {
            return;
        }

        // when a record file is re-processed, remove file data chunks from the same record file
        var cache = getFileContents();
        for (var entry : cache.asMap().entrySet()) {
            var fileId = entry.getKey();
            var contents = entry.getValue();
            var iter = contents.listIterator(contents.size());
            while (iter.hasPrevious()) {
                var fileData = iter.previous();
                if (fileData.getConsensusTimestamp() < consensusStart) {
                    break;
                }

                iter.remove();
            }

            if (contents.isEmpty()) {
                cache.invalidate(fileId);
            }
        }

        lastConsensusTimestamp.set(consensusStart - 1);
    }

    private Cache<EntityId, List<FileData>> buildCache() {
        return Caffeine.from(CaffeineSpec.parse(cacheProperties.getFileData())).build();
    }

    private static byte[] combine(List<FileData> contents) {
        if (contents.size() == 1) {
            return contents.getFirst().getFileData();
        }

        int size = 0;
        for (var content : contents) {
            size += content.getDataSize();
        }

        if (size == 0) {
            return ArrayUtils.EMPTY_BYTE_ARRAY;
        }

        byte[] combined = new byte[size];
        int offset = 0;
        for (var content : contents) {
            if (content.getDataSize() == 0) {
                continue;
            }

            System.arraycopy(content.getFileData(), 0, combined, offset, content.getDataSize());
            offset += content.getDataSize();
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
