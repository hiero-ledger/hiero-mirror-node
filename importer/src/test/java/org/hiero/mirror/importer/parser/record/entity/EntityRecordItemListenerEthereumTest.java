// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.converter.WeiBarTinyBarConverter.WEIBARS_TO_TINYBARS;
import static org.hiero.mirror.common.util.DomainUtils.EMPTY_BYTE_ARRAY;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.RAW_TX_TYPE_1;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.RAW_TX_TYPE_1_CALL_DATA;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.RAW_TX_TYPE_1_CALL_DATA_OFFLOADED;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionRecord.Builder;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hiero.mirror.common.domain.contract.ContractResult;
import org.hiero.mirror.common.domain.contract.ContractTransaction;
import org.hiero.mirror.common.domain.contract.ContractTransactionHash;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.ethereum.LegacyEthereumTransactionParserTest;
import org.hiero.mirror.importer.repository.ContractTransactionRepository;
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
    private static final long GAS_LIMIT = 5_750_000L;
    private static final long GAS_PRICE_TINYBARS = 50L;
    private static final Version HAPI_VERSION_0_46_0 = new Version(0, 46, 0);
    private static final long INITIAL_BALANCE_TINYBARS = 10_000_000L;
    private static final long MAX_GAS_ALLOWANCE_TINYBARS = 10_000_000_000L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long SIGNER_NONCE = 10L;

    private final ContractTransactionRepository contractTransactionRepository;
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

    /**
     * A legacy Ethereum contract create whose raw transaction carries the Parent contract creation bytecode. The
     * bytecode sidecar leaves initcode empty because it is already in the calldata.
     */
    @Test
    void ethereumContractCreatePersistsRuntimeAndInitBytecode() throws Exception {
        final var artifact = parentArtifact();
        final var initcode = hexBytecode(artifact.get("bytecode").asText());
        final var runtimeBytecode = hexBytecode(artifact.get("deployedBytecode").asText());
        final var contractId = recordItemBuilder.contractId();

        final var parent = recordItemBuilder
                .ethereumTransaction(true)
                .transactionBody(body -> body.clearCallData()
                        .setEthereumData(ByteString.copyFrom(legacyContractCreate(initcode)))
                        .setMaxGasAllowance(MAX_GAS_ALLOWANCE_TINYBARS))
                .record(record -> record.getReceiptBuilder().setContractID(contractId))
                .sidecarRecords(sidecars -> sidecars.add(TransactionSidecarRecord.newBuilder()
                        .setBytecode(ContractBytecode.newBuilder()
                                .setContractId(contractId)
                                .setInitcode(ByteString.EMPTY))))
                .build();

        final var child = childContractCreate(contractId, parent, runtimeBytecode);
        parseRecordItemsAndCommit(List.of(parent, child));

        final var contract =
                contractRepository.findById(EntityId.of(contractId).getId()).orElseThrow();
        assertThat(contract.getInitcode()).isEqualTo(initcode);
        assertThat(contract.getRuntimeBytecode()).isEqualTo(runtimeBytecode);
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
                () -> assertEquals(3, cryptoTransferRepository.count()),
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
                () -> assertEquals(3, cryptoTransferRepository.count()),
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
                () -> assertEquals(3, cryptoTransferRepository.count()),
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
        softly.assertThat(cryptoTransferRepository.count()).isEqualTo(3);
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
    void ethereumTransactionNoContractId() {
        // given
        final var result = ResponseCodeEnum.INSUFFICIENT_GAS;
        final var recordItem = recordItemBuilder
                .ethereumTransaction(true)
                .record(r -> r.setContractCreateResult(
                        r.getContractCreateResultBuilder().clearContractID().clearLogInfo()))
                .record(r -> r.getReceiptBuilder().setStatus(result))
                .sidecarRecords(List::clear)
                .build();
        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final var contractIds = List.of(0L, recordItem.getPayerAccountId().getId());

        // when
        parseRecordItemAndCommit(recordItem);

        softly.assertThat(contractResultRepository.findAll())
                .hasSize(1)
                .first()
                .returns(consensusTimestamp, ContractResult::getConsensusTimestamp)
                .returns(0L, ContractResult::getContractId);
        softly.assertThat(contractTransactionRepository.findAll())
                .hasSize(2)
                .allSatisfy(ct -> assertThat(ct)
                        .returns(consensusTimestamp, ContractTransaction::getConsensusTimestamp)
                        .returns(contractIds, ContractTransaction::getContractIds)
                        .returns(recordItem.getPayerAccountId().getId(), ContractTransaction::getPayerAccountId))
                .anyMatch(ct -> ct.getEntityId() == 0L);
        softly.assertThat(contractTransactionHashRepository.findAll())
                .hasSize(1)
                .first()
                .returns(consensusTimestamp, ContractTransactionHash::getConsensusTimestamp)
                .returns(0L, ContractTransactionHash::getEntityId)
                .returns(recordItem.getPayerAccountId().getId(), ContractTransactionHash::getPayerAccountId)
                .returns(result.getNumber(), ContractTransactionHash::getTransactionResult);
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
                () -> assertEquals(3, cryptoTransferRepository.count()),
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

    private RecordItem childContractCreate(
            final ContractID contractId, final RecordItem parent, final byte[] runtimeBytecode) {
        return recordItemBuilder
                .contractCreate(contractId)
                .transactionBody(body -> body.clearFileID().clearInitcode())
                .record(record -> record.setParentConsensusTimestamp(
                        parent.getTransactionRecord().getConsensusTimestamp()))
                .recordItem(item -> item.parent(parent).previous(parent))
                .sidecarRecords(sidecars -> {
                    for (final var sidecar : sidecars) {
                        if (sidecar.hasBytecode()) {
                            sidecar.getBytecodeBuilder()
                                    .setContractId(contractId)
                                    .setInitcode(ByteString.EMPTY)
                                    .setRuntimeBytecode(ByteString.copyFrom(runtimeBytecode));
                        }
                    }
                })
                .build();
    }

    private static byte[] hexBytecode(final String hex) {
        final var payload = hex.startsWith("0x") ? hex.substring(2) : hex;
        return HexFormat.of().parseHex(payload);
    }

    /**
     * Same shape as {@code EthereumClient.createContract}: legacy transaction, empty {@code to}, value from the Parent
     * contract initial balance, and creation bytecode as call data.
     */
    private static byte[] legacyContractCreate(final byte[] callData) {
        final var gasPrice = GAS_PRICE_TINYBARS * WEIBARS_TO_TINYBARS;
        final var value = INITIAL_BALANCE_TINYBARS * WEIBARS_TO_TINYBARS;
        return RLPEncoder.list(
                Integers.toBytes(0),
                Integers.toBytes(gasPrice),
                Integers.toBytes(GAS_LIMIT),
                new byte[0],
                Integers.toBytes(value),
                callData,
                HexFormat.of().parseHex("0277"),
                HexFormat.of().parseHex("f9fbff985d374be4a55f296915002eec11ac96f1ce2df183adf992baa9390b2f"),
                HexFormat.of().parseHex("0c1e867cc960d9c74ec2e6a662b7908ec4c8cc9f3091e886bcefbeb2290fb792"));
    }

    private static JsonNode parentArtifact() throws Exception {
        final var path = Path.of("").toAbsolutePath();
        var artifact = path.resolve("../test/src/test/resources/solidity/artifacts/contracts/Parent.sol/Parent.json");
        if (!artifact.toFile().isFile()) {
            artifact = path.resolve("test/src/test/resources/solidity/artifacts/contracts/Parent.sol/Parent.json");
        }
        return OBJECT_MAPPER.readTree(artifact.toFile());
    }
}
