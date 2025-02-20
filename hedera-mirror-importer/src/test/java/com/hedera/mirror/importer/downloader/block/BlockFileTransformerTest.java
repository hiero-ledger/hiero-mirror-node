// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block;

import static com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase.SUBMIT_MESSAGE;
import static com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase.TOKEN_AIRDROP;
import static com.hedera.mirror.common.util.DomainUtils.createSha384Digest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
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
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.util.Version;

@RequiredArgsConstructor
class BlockFileTransformerTest extends ImporterIntegrationTest {

    private static final Version HAPI_VERSION = new Version(0, 57, 0);
    private static final RecursiveComparisonConfiguration RECORD_ITEMS_COMPARISON_CONFIG =
            RecursiveComparisonConfiguration.builder()
                    .withIgnoredFields("parent", "previous")
                    .withEqualsForType(Object::equals, TransactionRecord.class)
                    .build();

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
    void defaultTransforms(TransactionType type, RecordItemBuilder.Builder<?> recordItem) {
        // given
        var expectedRecordItem = recordItem.customize(this::finalizer).build();
        var blockItem = blockItemBuilder.defaultBlockItem(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @ParameterizedTest
    @EnumSource(value = TransferType.class)
    void cryptoTransfer(TransferType transferType) {
        // given
        var expectedRecordItem = recordItemBuilder
                .cryptoTransfer(transferType)
                .customize(this::finalizer)
                .build();
        var expectedRecordItem2 = recordItemBuilder
                .cryptoTransfer(transferType)
                .recordItem(r -> r.transactionIndex(1))
                .customize(this::finalizer)
                .build();
        var blockItem1 = blockItemBuilder.cryptoTransfer(expectedRecordItem).build();
        var blockItem2 = blockItemBuilder.cryptoTransfer(expectedRecordItem2).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem1, blockItem2)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, List.of(expectedRecordItem, expectedRecordItem2));
            assertThat(items).map(RecordItem::getParent).containsOnlyNulls();
        });
    }

    private static Stream<Arguments> provideAliasAndExpectedEvmAddress() {
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
        // given
        var expectedRecordItem = recordItemBuilder
                .cryptoCreate()
                .record(r -> r.clearEvmAddress().setEvmAddress(expectedEvmAddress))
                .transactionBody(b -> b.clearAlias().setAlias(alias))
                .customize(this::finalizer)
                .build();
        var blockItem = blockItemBuilder.cryptoCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void cryptoCreateUnsuccessfulTransform() {
        // given
        var expectedRecordItem = recordItemBuilder
                .cryptoCreate()
                .receipt(r -> r.clearAccountID().setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .customize(this::finalizer)
                .build();
        var blockItem = blockItemBuilder.cryptoCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
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
                .transactionOutputs(Collections.emptyMap())
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
                .transactionOutputs(Collections.emptyMap())
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
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).isEmpty());
    }

    @Test
    void scheduleCreate() {
        // given
        var accountId = recordItemBuilder.accountId();
        var expectedRecordItem = recordItemBuilder
                .scheduleCreate()
                .receipt(r -> r.setScheduledTransactionID(
                        TransactionID.newBuilder().setAccountID(accountId).build()))
                .customize(this::finalizer)
                .build();
        var blockItem = blockItemBuilder.scheduleCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void scheduleCreateUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .scheduleCreate()
                .receipt(r -> r.clearScheduleID().setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .customize(this::finalizer)
                .build();
        var blockItem = blockItemBuilder.scheduleCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void scheduleSign(boolean triggerExecution) {
        // given
        var builder = recordItemBuilder.scheduleSign();
        if (triggerExecution) {
            builder.receipt(r -> r.setScheduledTransactionID(
                    TransactionID.newBuilder().setAccountID(recordItemBuilder.accountId())));
        }

        var expectedRecordItem = builder.customize(this::finalizer).build();
        var blockItem = blockItemBuilder.scheduleSign(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void scheduleSignUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .scheduleSign()
                .receipt(r -> r.setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .customize(this::finalizer)
                .build();
        var blockItem = blockItemBuilder.scheduleSign(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100})
    void utilPrng(int range) {
        // given
        var expectedRecordItem =
                recordItemBuilder.prng(range).customize(this::finalizer).build();
        var blockItem = blockItemBuilder.utilPrng(expectedRecordItem).build();
        var expectedRecordItemBytes = recordItemBuilder
                .prng(range)
                .recordItem(r -> r.transactionIndex(1))
                .customize(this::finalizer)
                .build();
        var blockItemBytes = blockItemBuilder.utilPrng(expectedRecordItemBytes).build();
        var blockFile =
                blockFileBuilder.items(List.of(blockItem, blockItemBytes)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> {
            assertRecordItems(items, List.of(expectedRecordItem, expectedRecordItemBytes));
            assertThat(items).map(RecordItem::getParent).containsOnlyNulls();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100})
    void utilPrngWhenStatusNotSuccess(int range) {
        // given
        var expectedRecordItem = recordItemBuilder
                .prng(range)
                .record(TransactionRecord.Builder::clearEntropy)
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .customize(this::finalizer)
                .build();
        var blockItem = blockItemBuilder.utilPrng(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void nodeCreateTransform() {
        // given
        var expectedRecordItem =
                recordItemBuilder.nodeCreate().customize(this::finalizer).build();
        var blockItem = blockItemBuilder.nodeCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void nodeCreateTransformWhenStatusIsNotSuccess() {
        // given
        var expectedRecordItem = recordItemBuilder
                .nodeCreate()
                .receipt(r -> r.clearNodeId().setStatus(ResponseCodeEnum.INVALID_NODE_ID))
                .customize(this::finalizer)
                .build();
        var blockItem = blockItemBuilder.nodeCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void fileCreateTransform() {
        // given
        var expectedRecordItem =
                recordItemBuilder.fileCreate().customize(this::finalizer).build();
        var blockItem = blockItemBuilder.fileCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void fileCreateTransformWhenStatusIsNotSuccess() {
        // given
        var expectedRecordItem = recordItemBuilder
                .fileCreate()
                .receipt(r -> r.clearFileID().setStatus(ResponseCodeEnum.AUTHORIZATION_FAILED))
                .customize(this::finalizer)
                .build();
        var blockItem = blockItemBuilder.fileCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void consensusCreateTopicTransform() {
        // given
        var expectedRecordItem = recordItemBuilder
                .consensusCreateTopic()
                .customize(this::finalizer)
                .build();
        var blockItem =
                blockItemBuilder.consensusCreateTopic(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void consensusCreateTopicTransformUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .consensusCreateTopic()
                .receipt(TransactionReceipt.Builder::clearTopicID)
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .customize(this::finalizer)
                .build();
        var blockItem =
                blockItemBuilder.consensusCreateTopic(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void consensusSubmitMessageTransform() {
        // given
        var expectedRecordItem = recordItemBuilder
                .consensusSubmitMessage()
                .customize(this::finalizer)
                .build();
        var blockItem =
                blockItemBuilder.consensusSubmitMessage(expectedRecordItem).build();
        var expectedFees = blockItem
                .transactionOutputs()
                .get(SUBMIT_MESSAGE)
                .getSubmitMessage()
                .getAssessedCustomFeesList();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void consensusSubmitMessageTransformUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .consensusSubmitMessage()
                .receipt(r ->
                        r.clearTopicRunningHash().clearTopicRunningHashVersion().clearTopicSequenceNumber())
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .customize(this::finalizer)
                .build();
        var blockItem =
                blockItemBuilder.consensusSubmitMessage(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void tokenAirdrop() {
        // given
        var expectedRecordItem =
                recordItemBuilder.tokenAirdrop().customize(this::finalizer).build();
        var blockItem = blockItemBuilder.tokenAirdrop(expectedRecordItem).build();
        var expectedFees = blockItem
                .transactionOutputs()
                .get(TOKEN_AIRDROP)
                .getTokenAirdrop()
                .getAssessedCustomFeesList();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void tokenAirdropUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenAirdrop()
                .receipt(r -> r.setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .build();
        var blockItem = blockItemBuilder.tokenAirdrop(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void tokenBurn() {
        // given
        var expectedRecordItem =
                recordItemBuilder.tokenBurn().customize(this::finalizer).build();
        var blockItem = blockItemBuilder.tokenBurn(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void tokenBurnUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenBurn()
                .receipt(r -> r.clearNewTotalSupply().setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .customize(this::finalizer)
                .build();
        var blockItem = blockItemBuilder.tokenBurn(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void tokenCreate() {
        // given
        var expectedRecordItem =
                recordItemBuilder.tokenCreate().customize(this::finalizer).build();
        var blockItem = blockItemBuilder.tokenCreate(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void tokenCreateUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenCreate()
                .receipt(r -> r.clearTokenID().setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .customize(this::finalizer)
                .build();
        var blockItem =
                blockItemBuilder.unsuccessfulTransaction(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenType.class,
            names = {"FUNGIBLE_COMMON", "NON_FUNGIBLE_UNIQUE"})
    void tokenMint(TokenType type) {
        // given
        var expectedRecordItem =
                recordItemBuilder.tokenMint(type).customize(this::finalizer).build();
        var blockItem = blockItemBuilder.tokenMint(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @Test
    void tokenMintUnsuccessful() {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenMint()
                .receipt(r -> r.clearNewTotalSupply().clearSerialNumbers())
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
                .customize(this::finalizer)
                .build();
        var blockItem = blockItemBuilder.tokenMint(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenType.class,
            names = {"FUNGIBLE_COMMON", "NON_FUNGIBLE_UNIQUE"})
    void tokenWipe(TokenType type) {
        // given
        var builder = recordItemBuilder.tokenWipe(type).customize(this::finalizer);
        var expectedRecordItem = type.equals(TokenType.NON_FUNGIBLE_UNIQUE)
                ? builder.transactionBody(t -> t.setAmount(0).addSerialNumbers(2))
                        .build()
                : builder.build();
        var blockItem = blockItemBuilder.tokenWipe(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    @ParameterizedTest
    @EnumSource(
            value = TokenType.class,
            names = {"FUNGIBLE_COMMON", "NON_FUNGIBLE_UNIQUE"})
    void tokenWipeUnsuccessful(TokenType type) {
        // given
        var expectedRecordItem = recordItemBuilder
                .tokenWipe(type)
                .receipt(r -> r.clearNewTotalSupply().setStatus(ResponseCodeEnum.INVALID_TRANSACTION))
                .customize(this::finalizer)
                .build();
        var blockItem = blockItemBuilder.tokenWipe(expectedRecordItem).build();
        var blockFile = blockFileBuilder.items(List.of(blockItem)).build();

        // when
        var recordFile = blockFileTransformer.transform(blockFile);

        // then
        assertRecordFile(recordFile, blockFile, items -> assertThat(items).containsExactly(expectedRecordItem));
    }

    private void assertRecordFile(RecordFile actual, BlockFile blockFile, Consumer<List<RecordItem>> itemsAssert) {
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

    private void assertRecordItems(List<RecordItem> actual, List<RecordItem> expected) {
        var expectedPreviousItems = new ArrayList<>(expected.subList(0, expected.size() - 1));
        expectedPreviousItems.addFirst(null);
        assertThat(actual)
                .usingRecursiveFieldByFieldElementComparator(RECORD_ITEMS_COMPARISON_CONFIG)
                .containsExactlyElementsOf(expected)
                .map(RecordItem::getPrevious)
                .containsExactlyElementsOf(expectedPreviousItems);
    }

    private void finalizer(RecordItemBuilder.Builder<?> builder) {
        builder.contractTransactionPredicate(null)
                .entityTransactionPredicate(null)
                .recordItem(r -> r.hapiVersion(HAPI_VERSION));
    }

    private ByteString getExpectedTransactionHash(RecordItem recordItem) {
        var digest = createSha384Digest();
        return ByteString.copyFrom(
                digest.digest(DomainUtils.toBytes(recordItem.getTransaction().getSignedTransactionBytes())));
    }
}
