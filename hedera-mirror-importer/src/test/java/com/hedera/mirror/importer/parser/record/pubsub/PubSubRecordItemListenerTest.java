// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.pubsub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.domain.PubSubMessage;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PubSubRecordItemListenerTest {
    private static final Long CONSENSUS_TIMESTAMP = 100L;
    private static final TransactionRecord DEFAULT_RECORD = TransactionRecord.newBuilder()
            .setConsensusTimestamp(Utility.instantToTimestamp(Instant.ofEpochSecond(0L, CONSENSUS_TIMESTAMP)))
            .setReceipt(TransactionReceipt.newBuilder()
                    .setStatus(ResponseCodeEnum.SUCCESS)
                    .build())
            .build();
    private static final FileID ADDRESS_BOOK_FILE_ID =
            FileID.newBuilder().setFileNum(102).build();

    private static final NodeAddressBook UPDATED = addressBook(3);

    private static final String TOPIC_NAME = "topic-name";

    @Mock(strictness = LENIENT)
    private AddressBookService addressBookService;

    @Mock
    private PubSubTemplate pubSubTemplate;

    @Mock
    private TransactionHandler transactionHandler;

    private PubSubProperties pubSubProperties;
    private PubSubRecordItemListener pubSubRecordItemListener;

    private static AccountAmount buildAccountAmount(long accountNum, long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(AccountID.newBuilder().setAccountNum(accountNum).build())
                .setAmount(amount)
                .build();
    }

    private static Transaction buildTransaction(Consumer<TransactionBody.Builder> transactionModifier) {
        TransactionBody.Builder transactionBodyBuilder = TransactionBody.newBuilder();
        transactionModifier.accept(transactionBodyBuilder);
        return Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(transactionBodyBuilder.build().toByteString())
                        .setSigMap(SignatureMap.getDefaultInstance())
                        .build()
                        .toByteString())
                .build();
    }

    private static PubSubMessage buildPubSubTransaction(RecordItem recordItem, Transaction transaction)
            throws InvalidProtocolBufferException {
        var pubSubTransaction = new PubSubMessage.Transaction(
                TransactionBody.parseFrom(SignedTransaction.parseFrom(transaction.getSignedTransactionBytes())
                        .getBodyBytes()),
                SignatureMap.getDefaultInstance());
        var entityId = EntityId.of(10);
        var transactionType = recordItem.getTransactionType();
        var transactionRecord = recordItem.getTransactionRecord();
        return new PubSubMessage(
                recordItem.getConsensusTimestamp(),
                entityId,
                transactionType,
                pubSubTransaction,
                transactionRecord,
                null);
    }

    private static NodeAddressBook addressBook(int size) {
        NodeAddressBook.Builder builder = NodeAddressBook.newBuilder();
        ServiceEndpoint defaultServiceEndpoint = ServiceEndpoint.newBuilder()
                .setIpAddressV4(ByteString.copyFrom(new byte[] {127, 0, 0, 1}))
                .setPort(443)
                .build();
        for (int i = 0; i < size; ++i) {
            builder.addNodeAddress(NodeAddress.newBuilder()
                    .addServiceEndpoint(defaultServiceEndpoint)
                    .build());
        }
        return builder.build();
    }

    @BeforeEach
    void beforeEach() {
        TransactionHandlerFactory transactionHandlerFactory = mock(TransactionHandlerFactory.class);
        pubSubProperties = new PubSubProperties();
        pubSubProperties.setTopicName(TOPIC_NAME);
        when(transactionHandlerFactory.get(any())).thenReturn(transactionHandler);
        doReturn(true).when(addressBookService).isAddressBook(EntityId.of(ADDRESS_BOOK_FILE_ID));
        when(transactionHandlerFactory.get(any())).thenReturn(transactionHandler);
        var responseFuture = mock(CompletableFuture.class);
        doReturn(responseFuture).when(pubSubTemplate).publish(any(), any(), any());
        pubSubRecordItemListener = new PubSubRecordItemListener(
                pubSubProperties, pubSubTemplate, addressBookService, transactionHandlerFactory);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testPubSubMessage() throws Exception {
        // given
        byte[] message = new byte[] {'a', 'b', 'c'};
        TopicID topicID = TopicID.newBuilder().setTopicNum(10L).build();
        EntityId topicIdEntity = EntityId.of(topicID);
        ConsensusSubmitMessageTransactionBody submitMessage = ConsensusSubmitMessageTransactionBody.newBuilder()
                .setMessage(ByteString.copyFrom(message))
                .setTopicID(topicID)
                .build();
        Transaction transaction = buildTransaction(builder -> builder.setConsensusSubmitMessage(submitMessage));

        // when
        doReturn(topicIdEntity).when(transactionHandler).getEntity(any());
        var successFuture = mock(CompletableFuture.class);
        when(pubSubTemplate.publish(any(), any(), any())).thenReturn(successFuture);
        doAnswer(invocationOnMock -> {
                    BiConsumer<String, Throwable> callback = invocationOnMock.getArgument(0);
                    callback.accept("{}", null);
                    return null;
                })
                .when(successFuture)
                .whenComplete(any());

        var recordItem = RecordItem.builder()
                .transactionRecord(DEFAULT_RECORD)
                .transaction(transaction)
                .build();
        pubSubRecordItemListener.onItem(recordItem);

        // then
        verify(successFuture).whenComplete(any());
        var pubSubMessage = assertPubSubMessage(buildPubSubTransaction(recordItem, transaction), 1);
        assertThat(pubSubMessage.getEntity()).isEqualTo(topicIdEntity);
        assertThat(pubSubMessage.getNonFeeTransfers()).isNull();
    }

    @Test
    void testPubSubMessageNullEntityId() throws Exception {
        // given
        byte[] message = new byte[] {'a', 'b', 'c'};
        TopicID topicID = TopicID.newBuilder().setTopicNum(10L).build();
        ConsensusSubmitMessageTransactionBody submitMessage = ConsensusSubmitMessageTransactionBody.newBuilder()
                .setMessage(ByteString.copyFrom(message))
                .setTopicID(topicID)
                .build();
        Transaction transaction = buildTransaction(builder -> builder.setConsensusSubmitMessage(submitMessage));
        // when
        doReturn(null).when(transactionHandler).getEntity(any());
        var recordItem = RecordItem.builder()
                .transactionRecord(DEFAULT_RECORD)
                .transaction(transaction)
                .build();
        pubSubRecordItemListener.onItem(recordItem);

        // then
        var pubSubMessage = assertPubSubMessage(buildPubSubTransaction(recordItem, transaction), 1);
        assertThat(pubSubMessage.getEntity()).isNull();
        assertThat(pubSubMessage.getNonFeeTransfers()).isNull();
    }

    @Test
    void testPubSubMessageWithNonFeeTransferAndNullEntityId() throws Exception {
        // given
        List<AccountAmount> nonFeeTransfers = new ArrayList<>();
        nonFeeTransfers.add(buildAccountAmount(10L, 100L));
        nonFeeTransfers.add(buildAccountAmount(11L, 111L));
        CryptoTransferTransactionBody cryptoTransfer = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(TransferList.newBuilder()
                        .addAccountAmounts(nonFeeTransfers.get(0))
                        .addAccountAmounts(nonFeeTransfers.get(1))
                        .build())
                .build();
        Transaction transaction = buildTransaction(builder -> builder.setCryptoTransfer(cryptoTransfer));
        var recordItem = RecordItem.builder()
                .transactionRecord(DEFAULT_RECORD)
                .transaction(transaction)
                .build();

        // when
        pubSubRecordItemListener.onItem(recordItem);

        // then
        var pubSubMessage = assertPubSubMessage(buildPubSubTransaction(recordItem, transaction), 1);
        assertThat(pubSubMessage.getEntity()).isNull();
        assertThat(pubSubMessage.getNonFeeTransfers()).isEqualTo(nonFeeTransfers);
    }

    @Test
    void testNonRetryableError() {
        // when
        CryptoTransferTransactionBody cryptoTransfer = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(TransferList.newBuilder().build())
                .build();
        Transaction transaction = buildTransaction(builder -> builder.setCryptoTransfer(cryptoTransfer));
        RecordItem recordItem = RecordItem.builder()
                .transactionRecord(DEFAULT_RECORD)
                .transaction(transaction)
                .build();

        // when
        when(pubSubTemplate.publish(any(), any(), any())).thenThrow(RuntimeException.class);

        // then
        assertThatThrownBy(() -> pubSubRecordItemListener.onItem(recordItem))
                .isInstanceOf(ParserException.class)
                .hasMessageContaining("Error sending transaction to pubsub");
        verify(pubSubTemplate, times(1)).publish(any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSendRetries() throws Exception {
        CryptoTransferTransactionBody cryptoTransfer = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(TransferList.newBuilder().build())
                .build();
        Transaction transaction = buildTransaction(builder -> builder.setCryptoTransfer(cryptoTransfer));
        pubSubProperties.setMaxSendAttempts(3);

        // when
        var failFuture = mock(CompletableFuture.class);
        when(pubSubTemplate.publish(any(), any(), any())).thenReturn(failFuture);
        doAnswer(invocationOnMock -> {
                    BiConsumer<String, Throwable> callback = invocationOnMock.getArgument(0);
                    callback.accept(null, new RuntimeException("error"));
                    return null;
                })
                .when(failFuture)
                .whenComplete(any());

        var recordItem = RecordItem.builder()
                .transactionRecord(DEFAULT_RECORD)
                .transaction(transaction)
                .build();
        pubSubRecordItemListener.onItem(recordItem);

        // then
        assertPubSubMessage(buildPubSubTransaction(recordItem, transaction), 4);
    }

    @Test
    void testNetworkAddressBookAppend() {
        // given
        byte[] fileContents = new byte[] {0b0, 0b1, 0b10};
        Transaction transaction = buildTransaction(builder -> {
            builder.setFileAppend(FileAppendTransactionBody.newBuilder()
                    .setFileID(ADDRESS_BOOK_FILE_ID)
                    .setContents(ByteString.copyFrom(fileContents))
                    .build());
        });

        // when
        EntityId entityId = EntityId.of(ADDRESS_BOOK_FILE_ID);
        doReturn(entityId).when(transactionHandler).getEntity(any());
        pubSubRecordItemListener.onItem(RecordItem.builder()
                .transactionRecord(DEFAULT_RECORD)
                .transaction(transaction)
                .build());

        // then
        FileData fileData = new FileData(100L, fileContents, entityId, TransactionType.FILEAPPEND.getProtoId());
        verify(addressBookService).update(fileData);
    }

    @Test
    void testNetworkAddressBookUpdate() {
        // given
        byte[] fileContents = UPDATED.toByteArray();
        var fileUpdate = FileUpdateTransactionBody.newBuilder()
                .setFileID(ADDRESS_BOOK_FILE_ID)
                .setContents(ByteString.copyFrom(fileContents))
                .build();
        Transaction transaction = buildTransaction(builder -> builder.setFileUpdate(fileUpdate));

        // when
        doReturn(EntityId.of(ADDRESS_BOOK_FILE_ID)).when(transactionHandler).getEntity(any());
        pubSubRecordItemListener.onItem(RecordItem.builder()
                .transactionRecord(DEFAULT_RECORD)
                .transaction(transaction)
                .build());

        // then
        FileData fileData = new FileData(
                100L, fileContents, EntityId.of(ADDRESS_BOOK_FILE_ID), TransactionType.FILEUPDATE.getProtoId());
        verify(addressBookService).update(fileData);
    }

    @CsvSource({
        "SUCCESS, true",
        "INVALID_ACCOUNT_ID, false",
        "INVALID_CONTRACT_ID, false",
        "INVALID_FILE_ID, false",
        "INVALID_SCHEDULE_ID, false",
        "INVALID_TOKEN_ID, false",
        "INVALID_TOPIC_ID, false",
    })
    @ParameterizedTest
    void testRecoverableErrorWithInvalidEntityId(ResponseCodeEnum receiptStatus, boolean expected) {
        // given
        int expectedRecoverableErrorCount = expected ? 1 : 0;
        ConsensusSubmitMessageTransactionBody submitMessage =
                ConsensusSubmitMessageTransactionBody.newBuilder().build();
        Transaction transaction = buildTransaction(builder -> builder.setConsensusSubmitMessage(submitMessage));
        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Utility.instantToTimestamp(Instant.ofEpochSecond(0L, CONSENSUS_TIMESTAMP)))
                .setReceipt(
                        TransactionReceipt.newBuilder().setStatus(receiptStatus).build())
                .build();
        var recordItem = RecordItem.builder()
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build();

        doThrow(InvalidEntityException.class).when(transactionHandler).getEntity(any());

        try (MockedStatic<Utility> mockUtility = mockStatic(Utility.class)) {
            // when
            pubSubRecordItemListener.onItem(recordItem);
            // then
            mockUtility.verify(
                    () -> Utility.handleRecoverableError(any(), any(), any()), times(expectedRecoverableErrorCount));
        }
    }

    @SuppressWarnings("unchecked")
    private PubSubMessage assertPubSubMessage(PubSubMessage pubSubMessage, int numSendTries) {
        Map<String, String> header = Map.of("consensusTimestamp", CONSENSUS_TIMESTAMP.toString());
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PubSubMessage> pubSubMessageCaptor = ArgumentCaptor.forClass(PubSubMessage.class);
        ArgumentCaptor<Map<String, String>> headerCaptor = ArgumentCaptor.forClass(Map.class);
        verify(pubSubTemplate, times(numSendTries))
                .publish(topicCaptor.capture(), pubSubMessageCaptor.capture(), headerCaptor.capture());

        var actualTopic = topicCaptor.getValue();
        assertThat(actualTopic).isEqualTo(TOPIC_NAME);

        var actualHeader = headerCaptor.getValue();
        assertThat(actualHeader).isEqualTo(header);

        var actualPubSubMessage = pubSubMessageCaptor.getValue();
        assertThat(actualPubSubMessage.getTransaction()).isEqualTo(pubSubMessage.getTransaction());
        assertThat(actualPubSubMessage.getTransactionRecord()).isEqualTo(DEFAULT_RECORD);
        return actualPubSubMessage;
    }
}
