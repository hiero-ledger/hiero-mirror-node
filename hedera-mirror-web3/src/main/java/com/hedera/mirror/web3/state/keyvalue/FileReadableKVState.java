// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.keyvalue;

import static com.hedera.services.utils.EntityIdUtils.toEntityId;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.mirror.common.domain.entity.AbstractEntity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.repository.FileDataRepository;
import com.hedera.mirror.web3.state.SystemFileLoader;
import com.hedera.mirror.web3.utils.Suppliers;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * This class serves as a repository layer between hedera app services read only state and the Postgres database in
 * mirror-node. The file data, which is read from the database is converted to the PBJ generated format, so that it can
 * properly be utilized by the hedera app components
 */
@Named
public class FileReadableKVState extends AbstractReadableKVState<FileID, File> {

    public static final String KEY = "FILES";
    private final FileDataRepository fileDataRepository;
    private final EntityRepository entityRepository;
    private final SystemFileLoader systemFileLoader;

    public FileReadableKVState(
            final FileDataRepository fileDataRepository,
            final EntityRepository entityRepository,
            SystemFileLoader systemFileLoader) {
        super(KEY);
        this.fileDataRepository = fileDataRepository;
        this.entityRepository = entityRepository;
        this.systemFileLoader = systemFileLoader;
    }

    @Override
    protected File readFromDataSource(@Nonnull FileID key) {
        final var timestamp = ContractCallContext.get().getTimestamp();
        final var fileEntityId = toEntityId(key);
        final var fileId = fileEntityId.getId();
        final var currentTimestamp = getCurrentTimestamp();

        if (systemFileLoader.isSystemFile(key)) {
            return systemFileLoader.load(key, currentTimestamp);
        }

        return timestamp
                .map(t -> fileDataRepository.getFileAtTimestamp(fileId, t))
                .orElseGet(() -> fileDataRepository.getFileAtTimestamp(fileId, currentTimestamp))
                .map(fileData -> mapToFile(fileData, key, timestamp))
                .orElse(null);
    }

    private File mapToFile(final FileData fileData, final FileID key, final Optional<Long> timestamp) {
        return File.newBuilder()
                .contents(Bytes.wrap(fileData.getFileData()))
                .expirationSecond(getExpirationSeconds(toEntityId(key), timestamp))
                .fileId(key)
                .build();
    }

    private Supplier<Long> getExpirationSeconds(final EntityId entityId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> timestamp
                .map(t -> entityRepository.findActiveByIdAndTimestamp(entityId.getId(), t))
                .orElseGet(() -> entityRepository.findByIdAndDeletedIsFalse(entityId.getId()))
                .map(AbstractEntity::getExpirationTimestamp)
                .orElse(null));
    }

    private long getCurrentTimestamp() {
        final var now = Instant.now();
        return DomainUtils.convertToNanos(now.getEpochSecond(), now.getNano());
    }
}
