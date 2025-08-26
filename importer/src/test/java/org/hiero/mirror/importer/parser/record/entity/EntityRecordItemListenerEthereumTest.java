// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.EMPTY_BYTE_ARRAY;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.RAW_TX_TYPE_1;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.RAW_TX_TYPE_1_CALL_DATA;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.RAW_TX_TYPE_1_CALL_DATA_OFFLOADED;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.TransactionRecord.Builder;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.contract.ContractTransactionHash;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.ethereum.LegacyEthereumTransactionParserTest;
import org.hiero.mirror.importer.repository.EthereumTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.util.Version;

@RequiredArgsConstructor
class EntityRecordItemListenerEthereumTest extends AbstractEntityRecordItemListenerTest {
    private static final Version HAPI_VERSION_0_46_0 = new Version(0, 46, 0);
    private static final long SIGNER_NONCE = 10L;

    private final EthereumTransactionRepository ethereumTransactionRepository;

    private static Stream<Arguments> provideSignerNonceArguments() {
        long incrementedNonce = 3L;
        return Stream.of(
                Arguments.of(true, false, SIGNER_NONCE),
                Arguments.of(true, true, incrementedNonce),
                Arguments.of(false, false, SIGNER_NONCE),
                Arguments.of(false, true, incrementedNonce));
    }

    @BeforeEach
    void before() {
        entityProperties.getPersist().setEthereumTransactions(true);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void ethereumTransactionEip1559(boolean create) {
        RecordItem recordItem = recordItemBuilder.ethereumTransaction(create).build();
        var txnRecord = recordItem.getTransactionRecord();
        var functionResult = create ? txnRecord.getContractCreateResult() : txnRecord.getContractCallResult();
        var senderId = EntityId.of(functionResult.getSenderId());
        Entity sender = domainBuilder
                .entity()
                .customize(e -> e.id(senderId.getId()).num(senderId.getNum()))
                .persist();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractRepository.count()),
                () -> assertEquals(1, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertEthereumTransaction(recordItem, sender, SIGNER_NONCE));
    }

    @ParameterizedTest
    @MethodSource("provideSignerNonceArguments")
    void ethereumTransactionSignerNonce(boolean create, boolean setPriorHapiVersion, long expectedNonce) {
        var builder = recordItemBuilder.ethereumTransaction(create);
        if (setPriorHapiVersion) {
            builder.record(x -> {
                        // This version has no signer nonce so create it with a new builder to remove the field
                        var contractFunctionResult = ContractFunctionResult.newBuilder()
                                .setSenderId(recordItemBuilder.accountId())
                                .build();
                        if (create) {
                            x.setContractCreateResult(contractFunctionResult);
                        } else {
                            x.setContractCallResult(contractFunctionResult);
                        }
                    })
                    .recordItem(r -> r.hapiVersion(HAPI_VERSION_0_46_0));
        }

        var recordItem = builder.build();
        var txnRecord = recordItem.getTransactionRecord();
        var functionResult = create ? txnRecord.getContractCreateResult() : txnRecord.getContractCallResult();
        var senderId = EntityId.of(functionResult.getSenderId());
        Entity sender = domainBuilder
                .entity()
                .customize(e -> e.id(senderId.getId()).num(senderId.getNum()))
                .persist();

        parseRecordItemAndCommit(recordItem);
        assertEthereumTransaction(recordItem, sender, expectedNonce);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void ethereumTransactionNullSignerNonce(boolean create) {
        var recordItem = recordItemBuilder
                .ethereumTransaction(create)
                .record(x -> x.setContractCallResult(
                        recordItemBuilder.contractFunctionResult().clearSignerNonce()))
                .build();

        var txnRecord = recordItem.getTransactionRecord();
        var functionResult = create ? txnRecord.getContractCreateResult() : txnRecord.getContractCallResult();
        var senderId = EntityId.of(functionResult.getSenderId());
        long nonceValue = 500L;
        Entity sender = domainBuilder
                .entity()
                .customize(e -> e.id(senderId.getId()).num(senderId.getNum()).ethereumNonce(nonceValue))
                .persist();

        // when
        parseRecordItemAndCommit(recordItem);

        // then
        var ethereumNonce = entityRepository.findById(sender.getId()).get().getEthereumNonce();
        // the nonce value is unchanged
        assertThat(ethereumNonce).isEqualTo(nonceValue);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void ethereumTransactionLegacy(boolean create) {
        RecordItem recordItem =
                getEthereumTransactionRecordItem(create, LegacyEthereumTransactionParserTest.LEGACY_RAW_TX);

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertEthereumTransaction(recordItem, null, SIGNER_NONCE));
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void ethereumTransactionEip155(boolean create) {
        RecordItem recordItem =
                getEthereumTransactionRecordItem(create, LegacyEthereumTransactionParserTest.EIP155_RAW_TX);

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertEthereumTransaction(recordItem, null, SIGNER_NONCE));
    }

    @Test
    void ethereumTransactionEip2950EmptyHash() {
        // given
        var recordItem = recordItemBuilder
                .ethereumTransaction(true)
                .record(Builder::clearEthereumHash)
                .transactionBody(b -> b.setEthereumData(ByteString.copyFrom(RAW_TX_TYPE_1_CALL_DATA_OFFLOADED)))
                .build();
        var body = recordItem.getTransactionBody().getEthereumTransaction();
        var fileId = EntityId.of(body.getCallData());
        domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(recordItem.getConsensusTimestamp() - 10)
                        .entityId(fileId)
                        .fileData(RAW_TX_TYPE_1_CALL_DATA.getBytes()))
                .persist();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var expectedHash = new Keccak.Digest256().digest(RAW_TX_TYPE_1);

        // when
        parseRecordItemAndCommit(recordItem);

        softly.assertThat(contractRepository.count()).isZero();
        softly.assertThat(contractResultRepository.findAll())
                .hasSize(1)
                .first()
                .returns(consensusTimestamp, ContractResult::getConsensusTimestamp)
                .returns(expectedHash, ContractResult::getTransactionHash);
        softly.assertThat(contractTransactionHashRepository.findAll())
                .hasSize(1)
                .first()
                .returns(consensusTimestamp, ContractTransactionHash::getConsensusTimestamp)
                .returns(expectedHash, ContractTransactionHash::getHash);
        softly.assertThat(cryptoTransferRepository.count()).isEqualTo(4);
        softly.assertThat(entityRepository.count()).isZero();
        softly.assertThat(ethereumTransactionRepository.findAll())
                .hasSize(1)
                .first()
                .returns(consensusTimestamp, EthereumTransaction::getConsensusTimestamp)
                .returns(fileId, EthereumTransaction::getCallDataId)
                .returns(EMPTY_BYTE_ARRAY, EthereumTransaction::getCallData)
                .returns(RAW_TX_TYPE_1_CALL_DATA_OFFLOADED, EthereumTransaction::getData)
                .returns(expectedHash, EthereumTransaction::getHash)
                .returns(body.getMaxGasAllowance(), EthereumTransaction::getMaxGasAllowance);
        softly.assertThat(transactionRepository.count()).isOne();
    }

    @Test
    void ethereumTransactionLegacyBadBytes() {
        var transactionBytes = RLPEncoder.list(Integers.toBytes(1), Integers.toBytes(2), Integers.toBytes(3));
        RecordItem recordItem = recordItemBuilder
                .ethereumTransaction(true)
                .transactionBody(x -> x.setEthereumData(ByteString.copyFrom(transactionBytes)))
                .build();

        assertDoesNotThrow(() -> parseRecordItemAndCommit(recordItem));
    }

    // Issue #11819 Invalid entity ID in failed create EthereumTransaction
    @Test
    void ethereumTransactionInvalidId() throws Exception {
        var transactionBytes = Hex.decodeHex(LegacyEthereumTransactionParserTest.EIP155_RAW_TX);
        var invalidId =
                ContractID.newBuilder().setContractNum(1514739994982350848L).build();
        var recordItem = recordItemBuilder
                .ethereumTransaction(true)
                .transactionBody(x -> x.setEthereumData(ByteString.copyFrom(transactionBytes)))
                .record(x -> x.setEthereumHash(ByteString.copyFrom(domainBuilder.bytes(32)))
                        .getContractCreateResultBuilder()
                        .setContractID(invalidId))
                .build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, contractRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, contractResultRepository.count()),
                () -> assertEquals(4, cryptoTransferRepository.count()),
                () -> assertEquals(1, ethereumTransactionRepository.count()),
                () -> assertThat(contractResultRepository.findAll()).hasSize(1),
                () -> assertEthereumTransaction(recordItem, null, SIGNER_NONCE));
    }

    @SneakyThrows
    private RecordItem getEthereumTransactionRecordItem(boolean create, String transactionBytesString) {
        var transactionBytes = Hex.decodeHex(transactionBytesString);
        return recordItemBuilder
                .ethereumTransaction(create)
                .transactionBody(x -> x.setEthereumData(ByteString.copyFrom(transactionBytes)))
                .record(x -> x.setEthereumHash(ByteString.copyFrom(domainBuilder.bytes(32))))
                .build();
    }

    private void assertEthereumTransaction(RecordItem recordItem, Entity sender, long expectedNonce) {
        long createdTimestamp = recordItem.getConsensusTimestamp();
        var ethTransaction =
                ethereumTransactionRepository.findById(createdTimestamp).get();
        var transactionBody = recordItem.getTransactionBody().getEthereumTransaction();
        var transactionRecord = recordItem.getTransactionRecord();

        var fileId = transactionBody.getCallData() == FileID.getDefaultInstance()
                ? null
                : EntityId.of(transactionBody.getCallData());
        assertThat(ethTransaction)
                .isNotNull()
                .returns(fileId, EthereumTransaction::getCallDataId)
                .returns(DomainUtils.toBytes(transactionBody.getEthereumData()), EthereumTransaction::getData)
                .returns(transactionBody.getMaxGasAllowance(), EthereumTransaction::getMaxGasAllowance)
                .returns(DomainUtils.toBytes(transactionRecord.getEthereumHash()), EthereumTransaction::getHash);

        if (sender != null) {
            var ethereumNonce = entityRepository.findById(sender.getId()).get().getEthereumNonce();
            assertThat(ethereumNonce).isEqualTo(expectedNonce);
        }
    }
}
