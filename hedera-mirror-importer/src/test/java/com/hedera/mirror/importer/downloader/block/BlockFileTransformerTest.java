/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.importer.downloader.block;

import static com.hedera.mirror.common.util.DomainUtils.createSha384Digest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3.Builder;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.hapi.block.stream.output.protoc.UtilPrngOutput;
import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.exception.ProtobufException;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.parser.domain.BlockFileBuilder;
import com.hedera.mirror.importer.parser.domain.BlockItemBuilder;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder.TransferType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.PendingAirdropRecord;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.util.Version;

@RequiredArgsConstructor
class BlockFileTransformerTest extends ImporterIntegrationTest {

    private static final int HAPI_VERSION_MINOR = 57;
    private static final Version HAPI_VERSION = new Version(0, HAPI_VERSION_MINOR);

    private final BlockFileBuilder blockFileBuilder;
    private final BlockItemBuilder blockItemBuilder;
    private final BlockFileTransformer blockFileTransformer;
    private static final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    private static Stream<Arguments> provideDefaultTransforms() {
        return Stream.of(
                Arguments.of(TransactionType.CONSENSUSDELETETOPIC, recordItemBuilder.consensusDeleteTopic()),
                Arguments.of(TransactionType.CONSENSUSUPDATETOPIC, recordItemBuilder.consensusUpdateTopic()),
                Arguments.of(TransactionType.CRYPTOADDLIVEHASH, recordItemBuilder.cryptoAddLiveHash()),
                Arguments.of(TransactionType.CRYPTOAPPROVEALLOWANCE, recordItemBuilder.cryptoApproveAllowance()),
                Arguments.of(TransactionType.CRYPTODELETE, recordItemBuilder.cryptoDelete()),
                Arguments.of(TransactionType.CRYPTODELETEALLOWANCE, recordItemBuilder.cryptoDeleteAllowance()),
                Arguments.of(TransactionType.CRYPTODELETELIVEHASH, recordItemBuilder.cryptoDeleteLiveHash()),
                Arguments.of(TransactionType.CRYPTOUPDATEACCOUNT, recordItemBuilder.cryptoUpdate()),
                Arguments.of(TransactionType.FILEAPPEND, recordItemBuilder.fileAppend()),
                Arguments.of(TransactionType.FILEDELETE, recordItemBuilder.fileDelete()),
                Arguments.of(TransactionType.FILEUPDATE, recordItemBuilder.fileUpdate()),
                Arguments.of(TransactionType.NODEDELETE, recordItemBuilder.nodeDelete()),
                Arguments.of(TransactionType.NODESTAKEUPDATE, recordItemBuilder.nodeStakeUpdate()),
                Arguments.of(TransactionType.NODEUPDATE, recordItemBuilder.nodeUpdate()),
                Arguments.of(TransactionType.SCHEDULEDELETE, recordItemBuilder.scheduleDelete()),
                Arguments.of(TransactionType.SYSTEMDELETE, recordItemBuilder.systemDelete()),
                Arguments.of(TransactionType.SYSTEMUNDELETE, recordItemBuilder.systemUndelete()),
                Arguments.of(TransactionType.TOKENASSOCIATE, recordItemBuilder.tokenAssociate()),
                Arguments.of(TransactionType.TOKENCANCELAIRDROP, recordItemBuilder.tokenCancelAirdrop()),
                Arguments.of(TransactionType.TOKENCLAIMAIRDROP, recordItemBuilder.tokenClaimAirdrop()),
                Arguments.of(TransactionType.TOKENDELETION, recordItemBuilder.tokenDelete()),
                Arguments.of(TransactionType.TOKENDISSOCIATE, recordItemBuilder.tokenDissociate()),
                Arguments.of(TransactionType.TOKENFEESCHEDULEUPDATE, recordItemBuilder.tokenFeeScheduleUpdate()),
                Arguments.of(TransactionType.TOKENGRANTKYC, recordItemBuilder.tokenGrantKyc()),
                Arguments.of(TransactionType.TOKENFREEZE, recordItemBuilder.tokenFreeze()),
                Arguments.of(TransactionType.TOKENPAUSE, recordItemBuilder.tokenPause()),
                Arguments.of(TransactionType.TOKENREJECT, recordItemBuilder.tokenReject()),
                Arguments.of(TransactionType.TOKENREVOKEKYC, recordItemBuilder.tokenRevokeKyc()),
                Arguments.of(TransactionType.TOKENUNFREEZE, recordItemBuilder.tokenUnfreeze()),
                Arguments.of(TransactionType.TOKENUNPAUSE, recordItemBuilder.tokenUnpause()),
                Arguments.of(TransactionType.TOKENUPDATE, recordItemBuilder.tokenUpdate()),
                Arguments.of(TransactionType.TOKENUPDATENFTS, recordItemBuilder.tokenUpdateNfts()),
                Arguments.of(TransactionType.UNCHECKEDSUBMIT, recordItemBuilder.uncheckedSubmit()),
                Arguments.of(TransactionType.UNKNOWN, recordItemBuilder.unknown()));
    }

    @ParameterizedTest(name = "Default transform for {0}")
    @MethodSource("provideDefaultTransforms")
    void defaultTransforms(
            TransactionType type, RecordItemBuilder.Builder<? extends Builder<? extends Builder<?>>> recordItem) {
        var expectedRecordItem =
                recordItem.recordItem(r -> r.hapiVersion(HAPI_VERSION)).build();
        var blockItem = blockItemBuilder.defaultBlockItem(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items)
                .hasSize(1)
                .first()
                .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                .returns(null, RecordItem::getPrevious)
                .extracting(RecordItem::getTransactionRecord)
                .returns(expectedTransactionHash, TransactionRecord::getTransactionHash));
    }

    @ParameterizedTest
    @EnumSource(value = TransferType.class)
    void cryptoTransfer(TransferType transferType) {
        // given
        var expectedRecordItem = recordItemBuilder
                .cryptoTransfer(transferType)
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);

        var expectedRecordItem2 = recordItemBuilder
                .cryptoTransfer(transferType)
                .recordItem(r -> r.hapiVersion(HAPI_VERSION).transactionIndex(1))
                .build();
        var expectedTransactionHash2 = getExpectedTransactionHash(expectedRecordItem2);

        var blockItem1 = blockItemBuilder.cryptoTransfer(expectedRecordItem).build();
        var expectedFees =
                blockItem1.transactionOutput().get(1).getCryptoTransfer().getAssessedCustomFeesList();
        var blockItem2 = blockItemBuilder.cryptoTransfer(expectedRecordItem2).build();
        var expectedFees2 =
                blockItem2.transactionOutput().get(1).getCryptoTransfer().getAssessedCustomFeesList();
        var blockFile = blockFileBuilder.items(List.of(blockItem1, blockItem2)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items).hasSize(2);
            assertThat(items)
                    .element(0)
                    .satisfies(recordItem -> assertRecordItem(recordItem, expectedRecordItem))
                    .returns(null, RecordItem::getPrevious)
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                    .returns(expectedFees, TransactionRecord::getAssessedCustomFeesList);
            assertThat(items)
                    .element(1)
                    .satisfies(recordItem -> assertRecordItem(recordItem, expectedRecordItem2))
                    .returns(items.iterator().next(), RecordItem::getPrevious)
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash2, TransactionRecord::getTransactionHash)
                    .returns(expectedFees2, TransactionRecord::getAssessedCustomFeesList);
        });
    }

    static Stream<Arguments> provideAliasAndExpectedEvmAddress() {
        RecordItemBuilder recordItemBuilder1 = new RecordItemBuilder();
        var randomAlias = recordItemBuilder1.bytes(20);
        return Stream.of(
                arguments(ByteString.EMPTY, ByteString.EMPTY),
                arguments(recordItemBuilder1.key().toByteString(), ByteString.EMPTY),
                arguments(randomAlias, randomAlias));
    }

    @ParameterizedTest
    @MethodSource("provideAliasAndExpectedEvmAddress")
    void cryptoCreateTransform(ByteString alias, ByteString expectedEvmAddress) {
        var expectedRecordItem = recordItemBuilder
                .cryptoCreate()
                .record(r -> r.clearEvmAddress().setEvmAddress(expectedEvmAddress))
                .transactionBody(b -> b.clearAlias().setAlias(alias))
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .build();

        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.cryptoCreate(expectedRecordItem).build();
        var expectedAccountId =
                blockItem.transactionOutput().getFirst().getAccountCreate().getCreatedAccountId();

        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items)
                .hasSize(1)
                .first()
                .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                .returns(null, RecordItem::getPrevious)
                .extracting(RecordItem::getTransactionRecord)
                .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                .returns(
                        expectedAccountId,
                        transactionRecord -> transactionRecord.getReceipt().getAccountID())
                .returns(expectedEvmAddress, TransactionRecord::getEvmAddress));
    }

    @Test
    void cryptoCreateUnsuccessfulTransform() {
        var expectedRecordItem = recordItemBuilder
                .cryptoCreate()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .receipt(r -> r.clearAccountID().setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .build();

        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.cryptoCreate(expectedRecordItem).build();

        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items)
                .hasSize(1)
                .first()
                .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                .returns(null, RecordItem::getPrevious)
                .extracting(RecordItem::getTransactionRecord)
                .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                .returns(
                        AccountID.getDefaultInstance(),
                        transactionRecord -> transactionRecord.getReceipt().getAccountID()));
    }

    @Test
    void corruptedTransactionBodyBytes() {
        // given
        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder()
                        .setSignedTransactionBytes(SignedTransaction.newBuilder()
                                .setBodyBytes(DomainUtils.fromBytes(domainBuilder.bytes(512)))
                                .build()
                                .toByteString())
                        .build())
                .transactionResult(TransactionResult.newBuilder().build())
                .transactionOutput(Collections.emptyList())
                .stateChanges(Collections.emptyList())
                .build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when, then
        assertThatThrownBy(() -> blockFileTransformer.transform(blockFile)).isInstanceOf(ProtobufException.class);
    }

    @Test
    void corruptedSignedTransactionBytes() {
        // given
        var blockItem = BlockItem.builder()
                .transaction(Transaction.newBuilder()
                        .setSignedTransactionBytes(DomainUtils.fromBytes(domainBuilder.bytes(256)))
                        .build())
                .transactionResult(TransactionResult.newBuilder().build())
                .transactionOutput(Collections.emptyList())
                .stateChanges(Collections.emptyList())
                .build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when, then
        assertThatThrownBy(() -> blockFileTransformer.transform(blockFile)).isInstanceOf(ProtobufException.class);
    }

    @Test
    void emptyBlockFile() {
        // given
        var blockFile = blockFileBuilder.items(Collections.emptyList()).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertThat(recordFile.getItems()).isEmpty();
        assertRecordFile(recordFile, blockFile, items -> {});
    }

    @Test
    void scheduleCreate() {
        // given
        var accountId = recordItemBuilder.accountId();
        var expectedRecordItem = recordItemBuilder
                .scheduleCreate()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .receipt(r -> r.setScheduledTransactionID(
                        TransactionID.newBuilder().setAccountID(accountId).build()))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.scheduleCreate(expectedRecordItem).build();
        var expectedScheduleId = blockItem
                .stateChanges()
                .getFirst()
                .getStateChangesList()
                .getFirst()
                .getMapUpdate()
                .getKey()
                .getScheduleIdKey()
                .getScheduleNum();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items)
                    .hasSize(1)
                    .first()
                    .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                    .returns(null, RecordItem::getPrevious)
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                    .extracting(TransactionRecord::getReceipt)
                    .returns(accountId, r -> r.getScheduledTransactionID().getAccountID())
                    .returns(expectedScheduleId, r -> r.getScheduleID().getScheduleNum());
        });
    }

    @Test
    void scheduleCreateUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .scheduleCreate()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .receipt(r -> r.setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.scheduleCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items)
                    .hasSize(1)
                    .first()
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(
                            ScheduleID.getDefaultInstance(), r -> r.getReceipt().getScheduleID())
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash);
        });
    }

    @Test
    void scheduleSign() {
        // given
        var accountId = recordItemBuilder.accountId();
        var expectedRecordItem = recordItemBuilder
                .scheduleSign()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .receipt(r -> r.setScheduledTransactionID(
                        TransactionID.newBuilder().setAccountID(accountId).build()))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.scheduleSign(expectedRecordItem).build();
        var expectedScheduleId =
                blockItem.transactionOutput().getFirst().getSignSchedule().getScheduledTransactionId();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items)
                    .hasSize(1)
                    .first()
                    .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                    .returns(null, RecordItem::getPrevious)
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                    .returns(
                            expectedScheduleId,
                            transactionRecord -> transactionRecord.getReceipt().getScheduledTransactionID());
        });
    }

    @Test
    void scheduleSignUnsuccessful() {
        // given
        var accountId = recordItemBuilder.accountId();
        var expectedRecordItem = recordItemBuilder
                .scheduleSign()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .receipt(r -> r.setScheduledTransactionID(TransactionID.newBuilder()
                                .setAccountID(accountId)
                                .build())
                        .setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.scheduleSign(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items)
                    .hasSize(1)
                    .first()
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                    .returns(TransactionID.getDefaultInstance(), r -> r.getReceipt()
                            .getScheduledTransactionID());
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100})
    void utilPrng(int range) {

        var expectedRecordItem = recordItemBuilder
                .prng(range)
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.utilPrng(expectedRecordItem).build();
        var expectedUtilPrng =
                blockItem.transactionOutput().getFirst().getUtilPrng().getPrngNumber();

        var expectedRecordItemBytes = recordItemBuilder
                .prng(range)
                .recordItem(r -> r.hapiVersion(HAPI_VERSION).transactionIndex(1))
                .build();
        var expectedTransactionHashBytes = getExpectedTransactionHash(expectedRecordItemBytes);
        var blockItemBytes = blockItemBuilder.utilPrng(expectedRecordItemBytes).build();
        var expectedUtilPrngBytes =
                blockItemBytes.transactionOutput().getFirst().getUtilPrng().getPrngBytes();

        var blockFile =
                blockFileBuilder.items(List.of(blockItem, blockItemBytes)).build();

        var recordFile = blockFileTransformer.transform(blockFile);

        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items).hasSize(2);
            assertThat(items)
                    .element(0)
                    .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                    .returns(null, RecordItem::getPrevious)
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                    .returns(expectedUtilPrng, TransactionRecord::getPrngNumber);

            assertThat(items)
                    .element(1)
                    .satisfies(item -> assertRecordItem(item, expectedRecordItemBytes))
                    .returns(items.iterator().next(), RecordItem::getPrevious)
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHashBytes, TransactionRecord::getTransactionHash)
                    .returns(expectedUtilPrngBytes, TransactionRecord::getPrngBytes);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100})
    void utilPrngWhenStatusNotSuccess(int range) {
        var expectedRecordItem = recordItemBuilder
                .prng(range)
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .record(TransactionRecord.Builder::clearEntropy)
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.utilPrng(expectedRecordItem).build();

        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();
        var recordFile = blockFileTransformer.transform(blockFile);

        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items).hasSize(1);
            assertThat(items)
                    .element(0)
                    .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                    .returns(null, RecordItem::getPrevious)
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                    .returns(0, TransactionRecord::getPrngNumber)
                    .returns(UtilPrngOutput.getDefaultInstance().getPrngBytes(), TransactionRecord::getPrngBytes);
        });
    }

    @Test
    void nodeCreateTransform() {
        var expectedRecordItem = recordItemBuilder
                .nodeCreate()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.nodeCreate(expectedRecordItem).build();
        var expectedNodeId = blockItem
                .stateChanges()
                .getFirst()
                .getStateChanges(3)
                .getMapUpdate()
                .getKey()
                .getEntityNumberKey()
                .getValue();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();
        var recordFile = blockFileTransformer.transform(blockFile);
        assertRecordFile(recordFile, blockFile, items -> assertThat(items)
                .hasSize(1)
                .first()
                .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                .returns(null, RecordItem::getPrevious)
                .extracting(RecordItem::getTransactionRecord)
                .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                .returns(
                        expectedNodeId,
                        transactionRecord -> transactionRecord.getReceipt().getNodeId()));
    }

    @Test
    void nodeCreateTransformWhenStatusIsNotSuccess() {
        // given
        var expectedRecordItem = recordItemBuilder
                .nodeCreate()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .receipt(r -> r.clearNodeId().setStatus(ResponseCodeEnum.INVALID_NODE_ID))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.nodeCreate(expectedRecordItem).build();

        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items)
                .hasSize(1)
                .first()
                .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                .returns(null, RecordItem::getPrevious)
                .extracting(RecordItem::getTransactionRecord)
                .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                .returns(0L, transactionRecord -> transactionRecord.getReceipt().getNodeId()));
    }

    @Test
    void fileCreateTransform() {
        // given
        var expectedRecordItem = recordItemBuilder
                .fileCreate()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.fileCreate(expectedRecordItem).build();
        var expectedFileId = blockItem
                .stateChanges()
                .getFirst()
                .getStateChanges(3)
                .getMapUpdate()
                .getKey()
                .getFileIdKey()
                .getFileNum();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items)
                .hasSize(1)
                .first()
                .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                .returns(null, RecordItem::getPrevious)
                .extracting(RecordItem::getTransactionRecord)
                .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                .returns(
                        expectedFileId,
                        transactionRecord ->
                                transactionRecord.getReceipt().getFileID().getFileNum()));
    }

    @Test
    void fileCreateTransformWhenStatusIsNotSuccess() {
        // given
        var expectedRecordItem = recordItemBuilder
                .fileCreate()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .receipt(r -> r.clearFileID().setStatus(ResponseCodeEnum.AUTHORIZATION_FAILED))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.fileCreate(expectedRecordItem).build();

        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items)
                .hasSize(1)
                .first()
                .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                .returns(null, RecordItem::getPrevious)
                .extracting(RecordItem::getTransactionRecord)
                .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                .returns(
                        0L,
                        transactionRecord ->
                                transactionRecord.getReceipt().getFileID().getFileNum()));
    }

    @Test
    void consensusCreateTopicTransform() {
        // given
        var expectedRecordItem = recordItemBuilder
                .consensusCreateTopic()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem =
                blockItemBuilder.consensusCreateTopic(expectedRecordItem).build();

        var expectedTopicId = blockItem
                .stateChanges()
                .getFirst()
                .getStateChanges(3)
                .getMapUpdate()
                .getKey()
                .getTopicIdKey()
                .getTopicNum();

        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items)
                .hasSize(1)
                .first()
                .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                .returns(null, RecordItem::getPrevious)
                .extracting(RecordItem::getTransactionRecord)
                .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                .returns(
                        expectedTopicId,
                        transactionRecord ->
                                transactionRecord.getReceipt().getTopicID().getTopicNum()));
    }

    @Test
    void consensusCreateTopicTransformUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .consensusCreateTopic()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .receipt(TransactionReceipt.Builder::clearTopicID)
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem =
                blockItemBuilder.consensusCreateTopic(expectedRecordItem).build();

        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items)
                .hasSize(1)
                .first()
                .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                .returns(null, RecordItem::getPrevious)
                .extracting(RecordItem::getTransactionRecord)
                .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                .returns(
                        0L,
                        transactionRecord ->
                                transactionRecord.getReceipt().getTopicID().getTopicNum()));
    }

    @Test
    void consensusSubmitMessageTransform() {
        // given
        var expectedRecordItem = recordItemBuilder
                .consensusSubmitMessage()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem =
                blockItemBuilder.consensusSubmitMessage(expectedRecordItem).build();

        var expectedFees =
                blockItem.transactionOutput().getFirst().getSubmitMessage().getAssessedCustomFeesList();
        var expectedRunningHash = blockItem
                .stateChanges()
                .getFirst()
                .getStateChanges(3)
                .getMapUpdate()
                .getValue()
                .getTopicValue()
                .getRunningHash();

        var expectedSequenceNumber = blockItem
                .stateChanges()
                .getFirst()
                .getStateChanges(3)
                .getMapUpdate()
                .getValue()
                .getTopicValue()
                .getSequenceNumber();

        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items)
                .hasSize(1)
                .first()
                .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                .returns(null, RecordItem::getPrevious)
                .extracting(RecordItem::getTransactionRecord)
                .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                .returns(
                        expectedRunningHash,
                        transactionRecord -> transactionRecord.getReceipt().getTopicRunningHash())
                .returns(
                        expectedSequenceNumber,
                        transactionRecord -> transactionRecord.getReceipt().getTopicSequenceNumber())
                .returns(expectedFees, TransactionRecord::getAssessedCustomFeesList));
    }

    @Test
    void consensusSubmitMessageTransformUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .consensusSubmitMessage()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .receipt(r -> r.setTopicRunningHash(ByteString.EMPTY))
                .incrementer((b, r) -> r.getReceiptBuilder().setTopicSequenceNumber(0))
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem =
                blockItemBuilder.consensusSubmitMessage(expectedRecordItem).build();

        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items)
                .hasSize(1)
                .first()
                .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                .returns(null, RecordItem::getPrevious)
                .extracting(RecordItem::getTransactionRecord)
                .returns(expectedTransactionHash, TransactionRecord::getTransactionHash));
    }

    @Test
    void tokenAirdrop() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenAirdrop()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.tokenAirdrop(expectedRecordItem).build();
        var expectedFees =
                blockItem.transactionOutput().getFirst().getTokenAirdrop().getAssessedCustomFeesList();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items)
                    .hasSize(1)
                    .first()
                    .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                    .returns(null, RecordItem::getPrevious)
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                    .returns(expectedFees, TransactionRecord::getAssessedCustomFeesList)
                    .extracting(TransactionRecord::getNewPendingAirdropsList)
                    .satisfies((Consumer<Collection<PendingAirdropRecord>>) airdrops -> assertThat(airdrops)
                            .hasSize(2)
                            .containsExactlyInAnyOrderElementsOf(
                                    expectedRecordItem.getTransactionRecord().getNewPendingAirdropsList()));
        });
    }

    @Test
    void tokenAirdropUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenAirdrop()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .receipt(r -> r.setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.tokenAirdrop(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items)
                    .hasSize(1)
                    .first()
                    .returns(null, RecordItem::getPrevious)
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                    .returns(Collections.emptyList(), TransactionRecord::getAssessedCustomFeesList)
                    .returns(Collections.emptyList(), TransactionRecord::getNewPendingAirdropsList);
        });
    }

    @Test
    void tokenBurn() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenBurn()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.tokenBurn(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items)
                    .hasSize(1)
                    .first()
                    .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                    .returns(null, RecordItem::getPrevious)
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash);
        });
    }

    @Test
    void tokenBurnUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenBurn()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .receipt(r -> r.setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.tokenBurn(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items)
                    .hasSize(1)
                    .first()
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                    .extracting(TransactionRecord::getReceipt)
                    .returns(ResponseCodeEnum.INVALID_TRANSACTION, TransactionReceipt::getStatus)
                    .returns(0L, TransactionReceipt::getNewTotalSupply);
        });
    }

    @Test
    void tokenCreate() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenCreate()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.tokenCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items)
                    .hasSize(1)
                    .first()
                    .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                    .returns(null, RecordItem::getPrevious)
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash);
        });
    }

    @Test
    void tokenCreateUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenCreate()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .receipt(r -> r.setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.tokenCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items)
                    .hasSize(1)
                    .first()
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                    .extracting(TransactionRecord::getReceipt)
                    .returns(TokenID.getDefaultInstance(), TransactionReceipt::getTokenID);
        });
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenType.class,
            names = {"FUNGIBLE_COMMON", "NON_FUNGIBLE_UNIQUE"})
    void tokenMint(TokenType type) {
        // given
        var builder = recordItemBuilder.tokenMint(type);
        if (type.equals(TokenType.NON_FUNGIBLE_UNIQUE)) {
            var tokenId = builder.build().getTransactionBody().getTokenMint().getToken();
            var tokenTransferList = TokenTransferList.newBuilder()
                    .setToken(tokenId)
                    .addNftTransfers(NftTransfer.newBuilder().setSerialNumber(1L))
                    .addNftTransfers(NftTransfer.newBuilder().setSerialNumber(2L))
                    .build();
            builder.record(r -> r.addAllTokenTransferLists(List.of(tokenTransferList)));
        }

        var expectedRecordItem =
                builder.recordItem(r -> r.hapiVersion(HAPI_VERSION)).build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.tokenMint(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items)
                    .hasSize(1)
                    .first()
                    .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                    .returns(null, RecordItem::getPrevious)
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash);
        });
    }

    @Test
    void tokenMintUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenMint()
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .receipt(r -> r.setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.tokenMint(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items)
                    .hasSize(1)
                    .first()
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                    .extracting(TransactionRecord::getReceipt)
                    .returns(0, TransactionReceipt::getSerialNumbersCount)
                    .returns(0L, TransactionReceipt::getNewTotalSupply);
        });
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenType.class,
            names = {"FUNGIBLE_COMMON", "NON_FUNGIBLE_UNIQUE"})
    void tokenWipe(TokenType type) {
        // given
        var builder = recordItemBuilder.tokenWipe(type).recordItem(r -> r.hapiVersion(HAPI_VERSION));
        var expectedRecordItem = type.equals(TokenType.NON_FUNGIBLE_UNIQUE)
                ? builder.transactionBody(t -> t.addSerialNumbers(2)).build()
                : builder.build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.tokenWipe(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items)
                    .hasSize(1)
                    .first()
                    .satisfies(item -> assertRecordItem(item, expectedRecordItem))
                    .returns(null, RecordItem::getPrevious)
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash);
        });
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenType.class,
            names = {"FUNGIBLE_COMMON", "NON_FUNGIBLE_UNIQUE"})
    void tokenWipeUnsuccessful(TokenType type) {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenWipe(type)
                .recordItem(r -> r.hapiVersion(HAPI_VERSION))
                .receipt(r -> r.setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .build();
        var expectedTransactionHash = getExpectedTransactionHash(expectedRecordItem);
        var blockItem = blockItemBuilder.tokenWipe(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertThat(items)
                    .hasSize(1)
                    .first()
                    .extracting(RecordItem::getTransactionRecord)
                    .returns(expectedTransactionHash, TransactionRecord::getTransactionHash)
                    .extracting(TransactionRecord::getReceipt)
                    .returns(0L, TransactionReceipt::getNewTotalSupply);
        });
    }

    private void assertRecordFile(
            RecordFile actual, BlockFile blockFile, Consumer<Collection<RecordItem>> itemsAssert) {
        var hapiProtoVersion = blockFile.getBlockHeader().getHapiProtoVersion();
        var softwareVersion = blockFile.getBlockHeader().getSoftwareVersion();
        assertThat(actual)
                .returns(blockFile.getBytes(), RecordFile::getBytes)
                .returns(blockFile.getConsensusEnd(), RecordFile::getConsensusEnd)
                .returns(blockFile.getConsensusStart(), RecordFile::getConsensusStart)
                .returns(blockFile.getCount(), RecordFile::getCount)
                .returns(blockFile.getDigestAlgorithm(), RecordFile::getDigestAlgorithm)
                .returns(StringUtils.EMPTY, RecordFile::getFileHash)
                .returns(0L, RecordFile::getGasUsed)
                .returns(hapiProtoVersion.getMajor(), RecordFile::getHapiVersionMajor)
                .returns(hapiProtoVersion.getMinor(), RecordFile::getHapiVersionMinor)
                .returns(hapiProtoVersion.getPatch(), RecordFile::getHapiVersionPatch)
                .returns(blockFile.getHash(), RecordFile::getHash)
                .returns(blockFile.getIndex(), RecordFile::getIndex)
                .returns(null, RecordFile::getLoadEnd)
                .returns(blockFile.getLoadStart(), RecordFile::getLoadStart)
                .returns(null, RecordFile::getLogsBloom)
                .returns(null, RecordFile::getMetadataHash)
                .returns(blockFile.getName(), RecordFile::getName)
                .returns(blockFile.getNodeId(), RecordFile::getNodeId)
                .returns(blockFile.getPreviousHash(), RecordFile::getPreviousHash)
                .returns(blockFile.getRoundEnd(), RecordFile::getRoundEnd)
                .returns(blockFile.getRoundStart(), RecordFile::getRoundStart)
                .returns(0, RecordFile::getSidecarCount)
                .satisfies(r -> assertThat(r.getSidecars()).isEmpty())
                .returns(blockFile.getSize(), RecordFile::getSize)
                .returns(softwareVersion.getMajor(), RecordFile::getSoftwareVersionMajor)
                .returns(softwareVersion.getMinor(), RecordFile::getSoftwareVersionMinor)
                .returns(softwareVersion.getPatch(), RecordFile::getSoftwareVersionPatch)
                .returns(blockFile.getVersion(), RecordFile::getVersion)
                .extracting(RecordFile::getItems)
                .satisfies(itemsAssert);
    }

    private void assertRecordItem(RecordItem recordItem, RecordItem expectedRecordItem) {
        assertThat(recordItem.getTransactionRecord().getMemo())
                .isEqualTo(expectedRecordItem.getTransactionRecord().getMemo());
        assertThat(recordItem)
                .usingRecursiveComparison()
                .ignoringFields(
                        "contractTransactionPredicate",
                        "entityTransactionPredicate",
                        "previous",
                        // Memo omitted here as this compares a ByteString to a String.
                        // The end result of the parsed memo value in persistence is equivalent whether the record file
                        // contains memoBytes or a memo String value.
                        "transactionRecord.memo_",
                        "transactionRecord.receipt_.memoizedIsInitialized",
                        "transactionRecord.receipt_.scheduleID_.memoizedHashCode",
                        "transactionRecord.receipt_.scheduleID_.memoizedIsInitialized",
                        "transactionRecord.receipt_.scheduleID_.memoizedSize",
                        "transactionRecord.assessedCustomFees_",
                        "transactionRecord.newPendingAirdrops_",
                        "transactionRecord.parentConsensusTimestamp_",
                        // Record file builder transaction hash is not generated based on transaction bytes, so these
                        // will not match
                        "transactionRecord.transactionHash_",
                        "transactionRecord.transactionID_.accountID_.memoizedHashCode",
                        "transactionRecord.transactionID_.accountID_.memoizedIsInitialized",
                        "transactionRecord.transactionID_.accountID_.memoizedSize",
                        "transactionRecord.transactionID_.memoizedIsInitialized",
                        "transactionRecord.transactionID_.memoizedSize",
                        "transactionRecord.transactionID_.transactionValidStart_.memoizedIsInitialized",
                        "transactionRecord.transactionID_.transactionValidStart_.memoizedSize",
                        "transactionRecord.receipt_.accountID_.memoizedHashCode",
                        "transactionRecord.receipt_.fileID_.memoizedHashCode",
                        "transactionRecord.receipt_.fileID_.memoizedSize",
                        "transactionRecord.receipt_.fileID_.memoizedIsInitialized",
                        "transactionRecord.receipt_.topicID_.memoizedHashCode",
                        "transactionRecord.receipt_.topicID_.memoizedSize",
                        "transactionRecord.receipt_.topicID_.memoizedIsInitialized",
                        "transactionRecord.receipt_.topicRunningHashVersion_")
                .isEqualTo(expectedRecordItem);
    }

    private ByteString getExpectedTransactionHash(RecordItem recordItem) {
        var digest = createSha384Digest();
        return ByteString.copyFrom(
                digest.digest(DomainUtils.toBytes(recordItem.getTransaction().getSignedTransactionBytes())));
    }
}
