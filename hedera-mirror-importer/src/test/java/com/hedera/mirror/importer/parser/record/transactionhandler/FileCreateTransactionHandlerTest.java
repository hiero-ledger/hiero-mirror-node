// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

class FileCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Mock
    private AddressBookService addressBookService;

    @Override
    protected TransactionHandler getTransactionHandler() {
        var fileDataHandler = new FileDataHandler(addressBookService, entityListener, entityProperties);
        return new FileCreateTransactionHandler(entityIdService, entityListener, fileDataHandler);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder().setFileCreate(FileCreateTransactionBody.getDefaultInstance());
    }

    @Override
    protected TransactionReceipt.Builder getTransactionReceipt(ResponseCodeEnum responseCodeEnum) {
        return TransactionReceipt.newBuilder()
                .setStatus(responseCodeEnum)
                .setFileID(FileID.newBuilder().setFileNum(DEFAULT_ENTITY_NUM).build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.FILE;
    }

    @Test
    void updateTransaction() {
        // Given
        var recordItem = recordItemBuilder.fileCreate().build();
        var transaction = domainBuilder.transaction().get();
        var fileData = ArgumentCaptor.forClass(FileData.class);
        var transactionBody = recordItem.getTransactionBody().getFileCreate();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onFileData(fileData.capture());
        assertThat(fileData.getValue())
                .returns(transaction.getConsensusTimestamp(), FileData::getConsensusTimestamp)
                .returns(transaction.getEntityId(), FileData::getEntityId)
                .returns(transactionBody.getContents().toByteArray(), FileData::getFileData)
                .returns(transaction.getType(), FileData::getTransactionType);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionDisabled() {
        // Given
        entityProperties.getPersist().setFiles(false);
        entityProperties.getPersist().setSystemFiles(false);
        var recordItem = recordItemBuilder.fileCreate().build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(addressBookService).isAddressBook(transaction.getEntityId());
        verifyNoMoreInteractions(addressBookService);
        verify(entityListener, never()).onFileData(any());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionPersistFilesFalse() {
        // Given
        var systemFileId = EntityId.of(0, 0, 120);
        entityProperties.getPersist().setFiles(false);
        entityProperties.getPersist().setSystemFiles(true);
        var recordItem = recordItemBuilder.fileCreate().build();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.entityId(systemFileId))
                .get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(addressBookService).isAddressBook(systemFileId);
        verifyNoMoreInteractions(addressBookService);
        verify(entityListener).onFileData(any());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionPersistSystemFilesFalse() {
        // Given
        var fileId = EntityId.of(0, 0, 1001);
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(false);
        var recordItem = recordItemBuilder.fileCreate().build();
        var transaction =
                domainBuilder.transaction().customize(t -> t.entityId(fileId)).get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(addressBookService).isAddressBook(fileId);
        verifyNoMoreInteractions(addressBookService);
        verify(entityListener).onFileData(any());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionAddressBook() {
        // Given
        var systemFileId = EntityId.of(0, 0, 102);
        var recordItem = recordItemBuilder.fileCreate().build();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.entityId(systemFileId))
                .get();
        doReturn(true).when(addressBookService).isAddressBook(systemFileId);

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(addressBookService).isAddressBook(systemFileId);
        verify(addressBookService).update(any());
        verify(entityListener, never()).onFileData(any());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
