// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.services.utils.EntityIdUtils.toEntityId;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Collections;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.EntityRepository;
import org.hiero.mirror.web3.repository.FileDataRepository;
import org.hiero.mirror.web3.state.SystemFileLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileReadableKVStateTest {

    private static final long SHARD = 0L;
    private static final long REALM = 1L;
    private static final long FILE_NUM = 1000L;
    private static final FileID FILE_ID = FileID.newBuilder()
            .shardNum(SHARD)
            .realmNum(REALM)
            .fileNum(FILE_NUM)
            .build();
    private static final File FILE = File.newBuilder().fileId(FILE_ID).build();
    private static final long FILE_ID_LONG = toEntityId(FILE_ID).getId();
    private static final long EXPIRATION_TIMESTAMP = 2_000_000_000L;
    private static final Optional<Long> TIMESTAMP = Optional.of(1234L);
    private static MockedStatic<ContractCallContext> contextMockedStatic;
    private FileData fileData;
    private Entity entity;

    @InjectMocks
    private FileReadableKVState fileReadableKVState;

    @Mock
    private FileDataRepository fileDataRepository;

    @Mock
    private EntityRepository entityRepository;

    @Mock
    private Bytes initBytecode;

    @Spy
    private ContractCallContext contractCallContext;

    @Mock
    private SystemFileLoader systemFileLoader;

    @BeforeAll
    static void initStaticMocks() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        contextMockedStatic.close();
    }

    @BeforeEach
    void setup() {
        fileData = new FileData();
        fileData.setFileData("Sample file data".getBytes());
        fileData.setConsensusTimestamp(TIMESTAMP.get());

        entity = new Entity();
        entity.setCreatedTimestamp(TIMESTAMP.get());
        entity.setShard(SHARD);
        entity.setRealm(REALM);
        entity.setNum(FILE_NUM);
        entity.setExpirationTimestamp(EXPIRATION_TIMESTAMP);
        entity.setDeleted(false);
        entity.setType(EntityType.FILE);

        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
    }

    @Test
    void fileFieldsMatchFileDataFields() {
        FileData fileDataTest = new FileData();
        fileDataTest.setFileData("file-contents".getBytes());

        long internalFileId = toEntityId(FILE_ID).getId();

        when(contractCallContext.getTimestamp()).thenReturn(TIMESTAMP);
        when(fileDataRepository.getFileAtTimestamp(internalFileId, TIMESTAMP.get()))
                .thenReturn(Optional.of(fileDataTest));
        when(entityRepository.findActiveByIdAndTimestamp(toEntityId(FILE_ID).getId(), TIMESTAMP.get()))
                .thenReturn(Optional.ofNullable(entity));

        File file = fileReadableKVState.get(FILE_ID);

        assertThat(file).isNotNull();
        assertThat(file.fileId()).isEqualTo(FILE_ID);
        assertThat(file.expirationSecondSupplier().get()).isEqualTo(entity.getExpirationTimestamp());
        assertThat(file.contents()).isEqualTo(Bytes.wrap(fileDataTest.getFileData()));
    }

    @Test
    void fileFieldsReturnNullWhenFileDataNotFound() {
        when(contractCallContext.getTimestamp()).thenReturn(TIMESTAMP);
        long fileIdLong = toEntityId(FILE_ID).getId();
        when(fileDataRepository.getFileAtTimestamp(fileIdLong, TIMESTAMP.get())).thenReturn(Optional.empty());

        File file = fileReadableKVState.get(FILE_ID);

        assertThat(file).isNull();
    }

    @Test
    void readFromDataSourceWithTimestamp() {
        when(contractCallContext.getTimestamp()).thenReturn(TIMESTAMP);
        when(fileDataRepository.getFileAtTimestamp(FILE_ID_LONG, TIMESTAMP.get()))
                .thenReturn(Optional.of(fileData));
        when(entityRepository.findActiveByIdAndTimestamp(toEntityId(FILE_ID).getId(), TIMESTAMP.get()))
                .thenReturn(Optional.ofNullable(entity));

        File result = fileReadableKVState.readFromDataSource(FILE_ID);

        assertThat(result).isNotNull();
        assertThat(result.fileId()).isEqualTo(FILE_ID);
        assertThat(result.expirationSecondSupplier().get()).isEqualTo(entity.getExpirationTimestamp());
        assertThat(result.contents()).isEqualTo(Bytes.wrap(fileData.getFileData()));
    }

    @Test
    void readFromDataSourceWithoutTimestamp() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(fileDataRepository.getFileAtTimestamp(anyLong(), anyLong())).thenReturn(Optional.of(fileData));
        when(entityRepository.findByIdAndDeletedIsFalse(toEntityId(FILE_ID).getId()))
                .thenReturn(Optional.of(entity));

        File result = fileReadableKVState.readFromDataSource(FILE_ID);

        assertThat(result).isNotNull();
        assertThat(result.fileId()).isEqualTo(FILE_ID);
        assertThat(result.expirationSecondSupplier().get()).isEqualTo(entity.getExpirationTimestamp());
        assertThat(result.contents()).isEqualTo(Bytes.wrap(fileData.getFileData()));
    }

    @Test
    void readFromDataSourceFileNotFound() {
        when(contractCallContext.getTimestamp()).thenReturn(TIMESTAMP);
        when(fileDataRepository.getFileAtTimestamp(FILE_ID_LONG, TIMESTAMP.get()))
                .thenReturn(Optional.empty());

        File result = fileReadableKVState.readFromDataSource(FILE_ID);

        assertThat(result).isNull();
    }

    @Test
    void readFromDataSourceSystemFile() {
        when(contractCallContext.getTimestamp()).thenReturn(TIMESTAMP);
        when(systemFileLoader.isSystemFile(FILE_ID)).thenReturn(true);
        when(systemFileLoader.load(any(), anyLong())).thenReturn(FILE);

        File result = fileReadableKVState.readFromDataSource(FILE_ID);

        assertThat(result).isEqualTo(FILE);
    }

    @Test
    void sizeIsAlwaysZero() {
        assertThat(fileReadableKVState.size()).isZero();
    }

    @Test
    void iterateReturnsEmptyIterator() {
        assertThat(fileReadableKVState.iterateFromDataSource()).isEqualTo(Collections.emptyIterator());
    }
}
