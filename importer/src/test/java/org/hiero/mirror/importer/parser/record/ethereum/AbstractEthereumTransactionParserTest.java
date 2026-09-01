// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.ethereum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.ACCESS_LIST_ADDRESS;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.ACCESS_LIST_ADDRESS_RAW;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.ACCESS_LIST_STORAGE_KEY;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.ACCESS_LIST_STORAGE_KEY_RAW;

import com.esaulpaugh.headlong.rlp.RLPDecoder;
import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.rlp.RLPItem;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hiero.mirror.common.domain.transaction.AccessList;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.exception.InvalidEthereumBytesException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@RequiredArgsConstructor
abstract class AbstractEthereumTransactionParserTest extends ImporterIntegrationTest {

    private static final String PADDED_ADDRESS = "0x0000000000000000000000000000000000000001";
    private static final String PADDED_STORAGE_KEY =
            "0x0000000000000000000000000000000000000000000000000000000000000081";
    private static final String SECOND_ACCESS_LIST_ADDRESS = "0x000000000000000000000000000000000000052d";
    private static final String SECOND_ACCESS_LIST_ADDRESS_RAW = "000000000000000000000000000000000000052d";
    private static final String SECOND_ACCESS_LIST_STORAGE_KEY =
            "0x0000000000000000000000000000000000000000000000000000000000000042";
    private static final String SECOND_ACCESS_LIST_STORAGE_KEY_RAW =
            "0000000000000000000000000000000000000000000000000000000000000042";

    protected final EthereumTransactionParser ethereumTransactionParser;

    protected abstract byte[] getTransactionBytes();

    protected abstract void validateEthereumTransaction(EthereumTransaction ethereumTransaction);

    @Test
    void decode() {
        final var ethereumTransaction = ethereumTransactionParser.decode(getTransactionBytes());
        validateEthereumTransaction(ethereumTransaction);
    }

    @ParameterizedTest
    @MethodSource("accessListTransactionTypes")
    void parseAccessListPadsShortRlpBytes(String transactionType) {
        final var accessList = parseAccessList(
                List.of(List.of(new byte[] {0x01}, List.of(new byte[] {(byte) 0x81}))), transactionType);

        assertThat(accessList).containsExactly(new AccessList(PADDED_ADDRESS, List.of(PADDED_STORAGE_KEY)));
    }

    @ParameterizedTest
    @MethodSource("accessListAddressesToPad")
    void encodeAccessListPadsAddress(String address, String expectedHex) {
        final var encoded =
                AbstractEthereumTransactionParser.encodeAccessList(List.of(new AccessList(address, List.of())));

        assertThat(encoded).singleElement().satisfies(entry -> {
            assertThat((byte[]) entry.getFirst()).isEqualTo(HexFormat.of().parseHex(expectedHex));
            assertThat(entry.get(1)).isEqualTo(List.of());
        });
    }

    @Test
    void encodeAccessListPadsAddressAndPreservesStorageKeys() {
        final var encoded = AbstractEthereumTransactionParser.encodeAccessList(
                List.of(new AccessList("0x1", List.of(ACCESS_LIST_STORAGE_KEY, SECOND_ACCESS_LIST_STORAGE_KEY))));

        assertThat(encoded).singleElement().satisfies(entry -> {
            assertThat((byte[]) entry.getFirst()).isEqualTo(HexFormat.of().parseHex(PADDED_ADDRESS.substring(2)));
            assertThat((List<?>) entry.get(1))
                    .satisfiesExactly(
                            key -> assertThat((byte[]) key)
                                    .isEqualTo(HexFormat.of().parseHex(ACCESS_LIST_STORAGE_KEY_RAW)),
                            key -> assertThat((byte[]) key)
                                    .isEqualTo(HexFormat.of().parseHex(SECOND_ACCESS_LIST_STORAGE_KEY_RAW)));
        });
    }

    @ParameterizedTest
    @MethodSource("accessListTransactionTypes")
    void parseEmptyAccessList(String transactionType) {
        final var accessList = parseAccessList(List.of(), transactionType);

        assertThat(accessList).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("accessListTransactionTypes")
    void parseAccessListWithSingleEntry(String transactionType) {
        final var accessList = parseAccessList(
                List.of(List.of(
                        HexFormat.of().parseHex(ACCESS_LIST_ADDRESS_RAW),
                        List.of(HexFormat.of().parseHex(ACCESS_LIST_STORAGE_KEY_RAW)))),
                transactionType);

        assertThat(accessList).containsExactly(new AccessList(ACCESS_LIST_ADDRESS, List.of(ACCESS_LIST_STORAGE_KEY)));
    }

    @ParameterizedTest
    @MethodSource("accessListTransactionTypes")
    void parseAccessListWithMultipleEntriesAndStorageKeys(String transactionType) {
        final var accessList = parseAccessList(
                List.of(
                        List.of(
                                HexFormat.of().parseHex(ACCESS_LIST_ADDRESS_RAW),
                                List.of(
                                        HexFormat.of().parseHex(ACCESS_LIST_STORAGE_KEY_RAW),
                                        HexFormat.of().parseHex(SECOND_ACCESS_LIST_STORAGE_KEY_RAW))),
                        List.of(
                                HexFormat.of().parseHex(SECOND_ACCESS_LIST_ADDRESS_RAW),
                                List.of(HexFormat.of().parseHex(ACCESS_LIST_STORAGE_KEY_RAW)))),
                transactionType);

        assertThat(accessList)
                .containsExactly(
                        new AccessList(
                                ACCESS_LIST_ADDRESS, List.of(ACCESS_LIST_STORAGE_KEY, SECOND_ACCESS_LIST_STORAGE_KEY)),
                        new AccessList(SECOND_ACCESS_LIST_ADDRESS, List.of(ACCESS_LIST_STORAGE_KEY)));
    }

    @ParameterizedTest
    @MethodSource("accessListTransactionTypes")
    void parseAccessListEmptyString(String transactionType) {
        final var accessListItem = RLPDecoder.RLP_STRICT.wrapItem(new byte[] {(byte) 0x80});

        assertThat(AbstractEthereumTransactionParser.parseAccessList(accessListItem, transactionType))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("accessListTransactionTypes")
    void parseAccessListNotList(String transactionType) {
        final var accessListItem =
                RLPDecoder.RLP_STRICT.wrapItem(RLPEncoder.string(HexFormat.of().parseHex(ACCESS_LIST_ADDRESS_RAW)));

        assertThat(AbstractEthereumTransactionParser.parseAccessList(accessListItem, transactionType))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("accessListTransactionTypes")
    void parseAccessListEntryNotList(String transactionType) {
        final var accessListItem = encodeAccessList(List.of(HexFormat.of().parseHex(ACCESS_LIST_ADDRESS_RAW)));

        assertThatThrownBy(() -> AbstractEthereumTransactionParser.parseAccessList(accessListItem, transactionType))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage(decodeError(transactionType, "Access list entry is not a list"));
    }

    @ParameterizedTest
    @MethodSource("accessListTransactionTypes")
    void parseAccessListStorageKeysNotList(String transactionType) {
        final var accessListItem = encodeAccessList(List.of(List.of(
                HexFormat.of().parseHex(ACCESS_LIST_ADDRESS_RAW),
                HexFormat.of().parseHex(ACCESS_LIST_STORAGE_KEY_RAW))));

        assertThatThrownBy(() -> AbstractEthereumTransactionParser.parseAccessList(accessListItem, transactionType))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage(decodeError(transactionType, "Access list entry storage keys is not a list"));
    }

    @ParameterizedTest
    @MethodSource("accessListTransactionTypes")
    void parseAccessListWrongSize(String transactionType) {
        final var accessListItem = encodeAccessList(List.of(List.of(
                HexFormat.of().parseHex(ACCESS_LIST_ADDRESS_RAW),
                List.of(HexFormat.of().parseHex(ACCESS_LIST_STORAGE_KEY_RAW)),
                HexFormat.of().parseHex(SECOND_ACCESS_LIST_STORAGE_KEY_RAW))));

        assertThatThrownBy(() -> AbstractEthereumTransactionParser.parseAccessList(accessListItem, transactionType))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage(decodeError(transactionType, "Access list entry size was 3 but expected 2"));
    }

    protected void assertEncodeProducesOriginalHash(byte[] transactionBytes) {
        final var parser = (AbstractEthereumTransactionParser) ethereumTransactionParser;
        final var encoded = parser.encode(parser.decode(transactionBytes));
        assertThat(encoded).isEqualTo(transactionBytes);
        assertThat(new Keccak.Digest256().digest(encoded)).isEqualTo(new Keccak.Digest256().digest(transactionBytes));
    }

    protected void assertEmptyAccessListFormatsProduceSameHash(byte[] emptyListTx, byte[] emptyStringTx) {
        final var parser = (AbstractEthereumTransactionParser) ethereumTransactionParser;
        final var hashFromEmptyList = new Keccak.Digest256().digest(parser.encode(parser.decode(emptyListTx)));
        final var hashFromEmptyString = new Keccak.Digest256().digest(parser.encode(parser.decode(emptyStringTx)));

        assertThat(hashFromEmptyString).isEqualTo(hashFromEmptyList);
        assertThat(hashFromEmptyList).isEqualTo(new Keccak.Digest256().digest(emptyListTx));
    }

    protected void assertGetHashWithOffloadedCallData(byte[] original, byte[] offloaded, String callDataHex) {
        final var expected = new Keccak.Digest256().digest(original);
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.fileData(callDataHex.getBytes(StandardCharsets.UTF_8)))
                .persist();
        final var actual = ethereumTransactionParser.getHash(
                DomainUtils.EMPTY_BYTE_ARRAY,
                fileData.getEntityId(),
                fileData.getConsensusTimestamp() + 1,
                offloaded,
                true);
        assertThat(actual).isEqualTo(expected);
    }

    private static Stream<Arguments> accessListTransactionTypes() {
        return Stream.of(Arguments.of("EIP1559"), Arguments.of("EIP2930"), Arguments.of("EIP7702"));
    }

    private static Stream<Arguments> accessListAddressesToPad() {
        final var one = PADDED_ADDRESS.substring(2);
        final var abc = "0000000000000000000000000000000000000abc";
        return Stream.of(
                Arguments.of("0x1", one),
                Arguments.of("0x01", one),
                Arguments.of("1", one),
                Arguments.of("01", one),
                Arguments.of(PADDED_ADDRESS, one),
                Arguments.of(one, one),
                Arguments.of("0xabc", abc),
                Arguments.of("abc", abc),
                Arguments.of(ACCESS_LIST_ADDRESS, ACCESS_LIST_ADDRESS_RAW),
                Arguments.of(ACCESS_LIST_ADDRESS_RAW, ACCESS_LIST_ADDRESS_RAW));
    }

    private static String decodeError(String transactionType, String detail) {
        return "Unable to decode %s ethereum transaction bytes, %s".formatted(transactionType, detail);
    }

    private static List<AccessList> parseAccessList(Iterable<?> entries, String transactionType) {
        return AbstractEthereumTransactionParser.parseAccessList(encodeAccessList(entries), transactionType);
    }

    private static RLPItem encodeAccessList(Iterable<?> entries) {
        return RLPDecoder.RLP_STRICT.wrapList(RLPEncoder.list(entries));
    }
}
