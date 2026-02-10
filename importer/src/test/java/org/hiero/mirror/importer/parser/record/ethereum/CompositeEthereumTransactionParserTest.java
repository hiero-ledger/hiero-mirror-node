// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.ethereum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.common.util.DomainUtils.EMPTY_BYTE_ARRAY;
import static org.hiero.mirror.importer.parser.domain.RecordItemBuilder.LONDON_RAW_TX;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.RAW_TX_TYPE_1;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.RAW_TX_TYPE_1_CALL_DATA;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.RAW_TX_TYPE_1_CALL_DATA_OFFLOADED;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.RAW_TX_TYPE_1_WITH_ACCESS_LIST;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.loadEthereumTransactions;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.populateFileData;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.exception.InvalidDatasetException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
final class CompositeEthereumTransactionParserTest extends AbstractEthereumTransactionParserTest {

    public static final String BERLIN_RAW_TX_1 =
            "01f87182012a8085a54f4c3c00832dc6c094000000000000000000000000000000000000052d8502540be40083123456c001a04d83230d6c19076fa42ef92f88d2cb0ae917b58640cc86f221a2e15b1736714fa03a4643759236b06b6abb31713ad694ab3b7ac5760f183c46f448260b08252b58";
    public static final String LONDON_RAW_TX_2 =
            "02f902e082012a80a00000000000000000000000000000000000000000000000000000000000004e20a0000000000000000000000000000000000000000000000000000000746a528800830f42408080b9024d608060405261023a806100136000396000f3fe60806040526004361061003f5760003560e01c806312065fe01461008f5780633ccfd60b146100ba5780636f64234e146100d1578063b6b55f251461012c575b3373ffffffffffffffffffffffffffffffffffffffff167ff1b03f708b9c39f453fe3f0cef84164c7d6f7df836df0796e1e9c2bce6ee397e346040518082815260200191505060405180910390a2005b34801561009b57600080fd5b506100a461015a565b6040518082815260200191505060405180910390f35b3480156100c657600080fd5b506100cf610162565b005b3480156100dd57600080fd5b5061012a600480360360408110156100f457600080fd5b81019080803573ffffffffffffffffffffffffffffffffffffffff169060200190929190803590602001909291905050506101ab565b005b6101586004803603602081101561014257600080fd5b81019080803590602001909291905050506101f6565b005b600047905090565b3373ffffffffffffffffffffffffffffffffffffffff166108fc479081150290604051600060405180830381858888f193505050501580156101a8573d6000803e3d6000fd5b50565b8173ffffffffffffffffffffffffffffffffffffffff166108fc829081150290604051600060405180830381858888f193505050501580156101f1573d6000803e3d6000fd5b505050565b80341461020257600080fd5b5056fea265627a7a72315820f8f84fc31a845064b5781e908316f3c591157962deabb0fd424ed54f256400f964736f6c63430005110032c001a01f7e8e436e6035ef7e5cd1387e2ad679e74d6a78a2736efe3dee72e531e28505a042b40a9cf56aad4530a5beaa8623f1ac3554d59ac1e927c672287eb45bfe7b8d";

    private static final String LESS_THAN_ERROR_MESSAGE =
            "Ethereum transaction bytes length is less than 2 bytes in length";

    public CompositeEthereumTransactionParserTest(CompositeEthereumTransactionParser ethereumTransactionParser) {
        super(ethereumTransactionParser);
    }

    @Override
    public byte[] getTransactionBytes() {
        return LONDON_RAW_TX;
    }

    @Test
    void decodeNullBytes() {
        assertThatThrownBy(() -> ethereumTransactionParser.decode(null))
                .isInstanceOf(InvalidDatasetException.class)
                .hasMessage(LESS_THAN_ERROR_MESSAGE);
    }

    @Test
    void decodeEmptyBytes() {
        assertThatThrownBy(() -> ethereumTransactionParser.decode(new byte[0]))
                .isInstanceOf(InvalidDatasetException.class)
                .hasMessage(LESS_THAN_ERROR_MESSAGE);
    }

    @Test
    void decodeLessThanMinByteSize() {
        assertThatThrownBy(() -> ethereumTransactionParser.decode(new byte[] {1}))
                .isInstanceOf(InvalidDatasetException.class)
                .hasMessage(LESS_THAN_ERROR_MESSAGE);
    }

    @Test
    void decodeEip1559WithAlternate2ndByte() {
        var ethereumTransaction = ethereumTransactionParser.decode(Hex.decode(LONDON_RAW_TX_2));
        validateEthereumTransaction(ethereumTransaction);
        assertThat(ethereumTransaction.getType()).isEqualTo(Eip1559EthereumTransactionParser.EIP1559_TYPE_BYTE);
    }

    @Test
    void decodeEip2930() {
        var ethereumTransaction = ethereumTransactionParser.decode(Hex.decode(BERLIN_RAW_TX_1));
        validateEthereumTransaction(ethereumTransaction);
        assertThat(ethereumTransaction.getType()).isEqualTo(Eip2930EthereumTransactionParser.EIP2930_TYPE_BYTE);
    }

    @Test
    void decodeEip7702() {
        var ethereumTransaction = ethereumTransactionParser.decode(Eip7702EthereumTransactionParserTest.EIP7702_RAW_TX);
        validateEthereumTransaction(ethereumTransaction);
        assertThat(ethereumTransaction.getType()).isEqualTo(Eip7702EthereumTransactionParser.EIP7702_TYPE_BYTE);
        assertThat(ethereumTransaction.getAuthorizationList()).isNotEmpty();
    }

    @Test
    void getHashEip7702() {
        var expected = new Keccak.Digest256().digest(Eip7702EthereumTransactionParserTest.EIP7702_RAW_TX);
        var actual = ethereumTransactionParser.getHash(
                EMPTY_BYTE_ARRAY,
                null,
                domainBuilder.timestamp(),
                Eip7702EthereumTransactionParserTest.EIP7702_RAW_TX,
                true);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void decodeUnsupportedEthereumTransaction() {
        byte[] unsupportedTx = Hex.decode("33" + BERLIN_RAW_TX_1.substring(2));
        assertThrows(InvalidDatasetException.class, () -> ethereumTransactionParser.decode(unsupportedTx));
    }

    @MethodSource("provideAllEthereumTransactions")
    @ParameterizedTest(name = "{0}")
    void getHash(String description, EthereumTransaction ethereumTransaction) {
        populateFileData(jdbcOperations);
        byte[] hash = ethereumTransactionParser.getHash(
                ethereumTransaction.getCallData(),
                ethereumTransaction.getCallDataId(),
                ethereumTransaction.getConsensusTimestamp(),
                ethereumTransaction.getData(),
                true);
        assertThat(hash).isEqualTo(ethereumTransaction.getHash());
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            true, true
            false, true
            true, false
            false, false
            """)
    void getHashType2CallDataFileData(boolean addHexPrefix, boolean useCurrentState) {
        // given
        byte[] expected = new Keccak.Digest256().digest(RAW_TX_TYPE_1);
        var fileData = domainBuilder
                .fileData()
                .customize(f -> {
                    String data = addHexPrefix ? "0x" + RAW_TX_TYPE_1_CALL_DATA : RAW_TX_TYPE_1_CALL_DATA;
                    f.fileData(data.getBytes(StandardCharsets.UTF_8));
                })
                .persist();
        if (!useCurrentState) {
            domainBuilder
                    .fileData()
                    .customize(f -> f.consensusTimestamp(fileData.getConsensusTimestamp() + 2)
                            .entityId(fileData.getEntityId())
                            .transactionType(TransactionType.FILEAPPEND.getProtoId()))
                    .persist();
        }

        // when
        var actual = ethereumTransactionParser.getHash(
                EMPTY_BYTE_ARRAY,
                fileData.getEntityId(),
                fileData.getConsensusTimestamp() + 1,
                RAW_TX_TYPE_1_CALL_DATA_OFFLOADED,
                useCurrentState);

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void getHashCannotReencodeWithAccessList(CapturedOutput capturedOutput) {
        // given
        long consensusTimestamp = domainBuilder.timestamp();
        String expectedMessage =
                "Re-encoding ethereum transaction at %d with access list is unsupported".formatted(consensusTimestamp);

        // when
        var actual = ethereumTransactionParser.getHash(
                EMPTY_BYTE_ARRAY, domainBuilder.entityId(), consensusTimestamp, RAW_TX_TYPE_1_WITH_ACCESS_LIST, true);

        // then
        softly.assertThat(actual).isEmpty();
        softly.assertThat(capturedOutput.getAll()).contains(expectedMessage);
    }

    @Test
    void getHashMalformedCallDataFileData(CapturedOutput capturedOutput) {
        // given
        var fileData = domainBuilder
                .fileData()
                .customize(f -> f.fileData(new byte[] {(byte) 0xff}))
                .persist();
        long consensusTimestamp = fileData.getConsensusTimestamp() + 1;
        var fileId = fileData.getEntityId();
        var expectedMessages = List.of(
                "Failed to decode contract bytecode from file %s org.bouncycastle.util.encoders.DecoderException"
                        .formatted(fileId),
                "Failed to read call data from file %s for ethereum transaction at %d"
                        .formatted(fileId, consensusTimestamp));

        // when
        var actual = ethereumTransactionParser.getHash(
                EMPTY_BYTE_ARRAY, fileData.getEntityId(), consensusTimestamp, RAW_TX_TYPE_1, true);

        // then
        softly.assertThat(actual).isEmpty();
        softly.assertThat(capturedOutput.getAll()).contains(expectedMessages);
    }

    @Test
    void getHashMissingCallDataFileData(CapturedOutput capturedOutput) {
        // given
        long consensusTimestamp = domainBuilder.timestamp();
        var fileEntityId = domainBuilder.entityId();
        String expectedMessage = "Failed to read call data from file %s for ethereum transaction at %d"
                .formatted(fileEntityId, consensusTimestamp);

        // when
        var actual = ethereumTransactionParser.getHash(
                EMPTY_BYTE_ARRAY, fileEntityId, consensusTimestamp, RAW_TX_TYPE_1, true);

        // then
        softly.assertThat(actual).isEmpty();
        softly.assertThat(capturedOutput.getAll()).contains(expectedMessage);
    }

    @Test
    void getHashUnsupportedEthereumTransactionType(CapturedOutput capturedOutput) {
        // given
        byte[] rawBytes = RLPEncoder.sequence(new byte[] {0x10}, List.of(new byte[] {0x01}, new byte[] {0x02}));
        String expectedMessage = "Unsupported Ethereum transaction data type";

        // when
        var actual = ethereumTransactionParser.getHash(
                EMPTY_BYTE_ARRAY, domainBuilder.entityId(), domainBuilder.timestamp(), rawBytes, true);

        // then
        softly.assertThat(actual).isEmpty();
        softly.assertThat(capturedOutput.getAll()).contains(expectedMessage);
    }

    @Override
    protected void validateEthereumTransaction(EthereumTransaction ethereumTransaction) {
        assertThat(ethereumTransaction).isNotNull().satisfies(t -> assertThat(t.getChainId())
                .isNotEmpty());
    }

    private static Stream<Arguments> provideAllEthereumTransactions() {
        return loadEthereumTransactions().stream()
                .map(ethereumTransaction -> Arguments.of(
                        String.format("Ethereum transaction at %d", ethereumTransaction.getConsensusTimestamp()),
                        ethereumTransaction));
    }
}
