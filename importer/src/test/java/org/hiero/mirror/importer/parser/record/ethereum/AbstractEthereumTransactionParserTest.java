// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.ethereum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.ACCESS_LIST_ADDRESS;
import static org.hiero.mirror.importer.parser.record.ethereum.EthereumTransactionTestUtility.ACCESS_LIST_STORAGE_KEY;

import com.esaulpaugh.headlong.rlp.RLPDecoder;
import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.rlp.RLPItem;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.AccessList;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.exception.InvalidEthereumBytesException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@RequiredArgsConstructor
abstract class AbstractEthereumTransactionParserTest extends ImporterIntegrationTest {

    private static final String SECOND_ACCESS_LIST_ADDRESS = "000000000000000000000000000000000000052d";
    private static final String SECOND_ACCESS_LIST_STORAGE_KEY =
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
    void parseEmptyAccessList(String transactionType) {
        final var accessList = parseAccessList(List.of(), transactionType);

        assertThat(accessList).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("accessListTransactionTypes")
    void parseAccessListWithSingleEntry(String transactionType) {
        final var accessList = parseAccessList(
                List.of(List.of(
                        HexFormat.of().parseHex(ACCESS_LIST_ADDRESS),
                        List.of(HexFormat.of().parseHex(ACCESS_LIST_STORAGE_KEY)))),
                transactionType);

        assertThat(accessList).containsExactly(new AccessList(ACCESS_LIST_ADDRESS, List.of(ACCESS_LIST_STORAGE_KEY)));
    }

    @ParameterizedTest
    @MethodSource("accessListTransactionTypes")
    void parseAccessListWithMultipleEntriesAndStorageKeys(String transactionType) {
        final var accessList = parseAccessList(
                List.of(
                        List.of(
                                HexFormat.of().parseHex(ACCESS_LIST_ADDRESS),
                                List.of(
                                        HexFormat.of().parseHex(ACCESS_LIST_STORAGE_KEY),
                                        HexFormat.of().parseHex(SECOND_ACCESS_LIST_STORAGE_KEY))),
                        List.of(
                                HexFormat.of().parseHex(SECOND_ACCESS_LIST_ADDRESS),
                                List.of(HexFormat.of().parseHex(ACCESS_LIST_STORAGE_KEY)))),
                transactionType);

        assertThat(accessList)
                .containsExactly(
                        new AccessList(
                                ACCESS_LIST_ADDRESS, List.of(ACCESS_LIST_STORAGE_KEY, SECOND_ACCESS_LIST_STORAGE_KEY)),
                        new AccessList(SECOND_ACCESS_LIST_ADDRESS, List.of(ACCESS_LIST_STORAGE_KEY)));
    }

    @ParameterizedTest
    @MethodSource("accessListTransactionTypes")
    void parseAccessListNotList(String transactionType) {
        final var accessListItem =
                RLPDecoder.RLP_STRICT.wrapItem(RLPEncoder.string(HexFormat.of().parseHex(ACCESS_LIST_ADDRESS)));

        assertThatThrownBy(() -> AbstractEthereumTransactionParser.parseAccessList(accessListItem, transactionType))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage(decodeError(transactionType, "Access list is not a list"));
    }

    @ParameterizedTest
    @MethodSource("accessListTransactionTypes")
    void parseAccessListEntryNotList(String transactionType) {
        final var accessListItem = encodeAccessList(List.of(HexFormat.of().parseHex(ACCESS_LIST_ADDRESS)));

        assertThatThrownBy(() -> AbstractEthereumTransactionParser.parseAccessList(accessListItem, transactionType))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage(decodeError(transactionType, "Access list entry is not a list"));
    }

    @ParameterizedTest
    @MethodSource("accessListTransactionTypes")
    void parseAccessListStorageKeysNotList(String transactionType) {
        final var accessListItem = encodeAccessList(List.of(List.of(
                HexFormat.of().parseHex(ACCESS_LIST_ADDRESS), HexFormat.of().parseHex(ACCESS_LIST_STORAGE_KEY))));

        assertThatThrownBy(() -> AbstractEthereumTransactionParser.parseAccessList(accessListItem, transactionType))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage(decodeError(transactionType, "Access list entry storage keys is not a list"));
    }

    @ParameterizedTest
    @MethodSource("accessListTransactionTypes")
    void parseAccessListWrongSize(String transactionType) {
        final var accessListItem = encodeAccessList(List.of(List.of(
                HexFormat.of().parseHex(ACCESS_LIST_ADDRESS),
                List.of(HexFormat.of().parseHex(ACCESS_LIST_STORAGE_KEY)),
                HexFormat.of().parseHex(SECOND_ACCESS_LIST_STORAGE_KEY))));

        assertThatThrownBy(() -> AbstractEthereumTransactionParser.parseAccessList(accessListItem, transactionType))
                .isInstanceOf(InvalidEthereumBytesException.class)
                .hasMessage(decodeError(transactionType, "Access list entry size was 3 but expected 2"));
    }

    private static Stream<Arguments> accessListTransactionTypes() {
        return Stream.of(Arguments.of("EIP1559"), Arguments.of("EIP2930"), Arguments.of("EIP7702"));
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
