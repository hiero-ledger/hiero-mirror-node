// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.ethereum;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.util.ResourceUtils;

@UtilityClass
public class EthereumTransactionTestUtility {

    // Data used to test type 1 with call data offloaded, copied from services test case
    public static final String RAW_TX_TYPE_1_CALL_DATA = "123456";
    public static final byte[] RAW_TX_TYPE_1 = HexFormat.of()
            .parseHex(
                    "01" // type
                            + "f873" // total length
                            + "82012a" // chain id => 82 - 80 = 2 (hex) = 2 (dec) bytes length
                            + "82160c" // nonce  => same length
                            + "85a54f4c3c00" // gas price => 5 bytes
                            + "832dc6c0" // gas limit => 3 bytes
                            + "94000000000000000000000000000000000000052d" // to => 94 - 80 = 14 (hex) = 20 (dec) bytes
                            + "8502540be400" // value => 5 bytes
                            + "83" + RAW_TX_TYPE_1_CALL_DATA // calldata => 3 bytes
                            + "c0" // empty access list => by the RLP definitions, an empty list is encoded with c0
                            + "01" // v
                            + "a0abb9e9c510716df2988cf626734ee50dcd9f41d30d638220712b5fe33fe4c816" // r => a0 - 80 = 80
                            // (hex) =
                            // 128 (dec) bytes
                            + "a0249a72e1479b61e00d4f20308577bb63167d71b26138ee5229ca1cb3c49a2e53" // same
                    );
    public static final byte[] RAW_TX_TYPE_1_CALL_DATA_OFFLOADED = HexFormat.of()
            .parseHex(
                    "01" // type
                            + "f870" // total length, 3 bytes shorter than the original
                            + "82012a" // chain id => 82 - 80 = 2 (hex) = 2 (dec) bytes length
                            + "82160c" // nonce  => same length
                            + "85a54f4c3c00" // gas price => 5 bytes
                            + "832dc6c0" // gas limit => 3 bytes
                            + "94000000000000000000000000000000000000052d" // to => 94 - 80 = 14 (hex) = 20 (dec) bytes
                            + "8502540be400" // value => 5 bytes
                            + "80" // calldata => offloaded to file
                            + "c0" // empty access list => by the RLP definitions, an empty list is encoded with c0
                            + "01" // v
                            + "a0abb9e9c510716df2988cf626734ee50dcd9f41d30d638220712b5fe33fe4c816" // r => a0 - 80 = 80
                            // (hex) =
                            // 128 (dec) bytes
                            + "a0249a72e1479b61e00d4f20308577bb63167d71b26138ee5229ca1cb3c49a2e53" // same
                    );
    public static final String ACCESS_LIST_ADDRESS = "a02457e5dfd32bda5fc7e1f1b008aa5979568150";
    public static final String ACCESS_LIST_STORAGE_KEY =
            "0000000000000000000000000000000000000000000000000000000000000081";

    public static final byte[] EIP_2930_RAW_TX_WITH_ACCESS_LIST = RLPEncoder.sequence(
            Integers.toBytes(1),
            List.of(
                    HexFormat.of().parseHex("012a"),
                    Integers.toBytes(5644),
                    HexFormat.of().parseHex("a54f4c3c00"),
                    Integers.toBytes(3_000_000),
                    HexFormat.of().parseHex("000000000000000000000000000000000000052d"),
                    HexFormat.of().parseHex("02540be400"),
                    HexFormat.of().parseHex("123456"),
                    List.of(List.of(
                            HexFormat.of().parseHex(ACCESS_LIST_ADDRESS),
                            List.of(HexFormat.of().parseHex(ACCESS_LIST_STORAGE_KEY)))),
                    Integers.toBytes(1),
                    HexFormat.of().parseHex("abb9e9c510716df2988cf626734ee50dcd9f41d30d638220712b5fe33fe4c816"),
                    HexFormat.of().parseHex("249a72e1479b61e00d4f20308577bb63167d71b26138ee5229ca1cb3c49a2e53")));

    public static final byte[] LONDON_RAW_TX_WITH_ACCESS_LIST = RLPEncoder.sequence(
            Integers.toBytes(2),
            List.of(
                    HexFormat.of().parseHex("012a"),
                    Integers.toBytes(2),
                    HexFormat.of().parseHex("2f"),
                    HexFormat.of().parseHex("2f"),
                    Integers.toBytes(98_304),
                    HexFormat.of().parseHex("7e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181"),
                    HexFormat.of().parseHex("0de0b6b3a7640000"),
                    HexFormat.of().parseHex("123456"),
                    List.of(List.of(
                            HexFormat.of().parseHex(ACCESS_LIST_ADDRESS),
                            List.of(HexFormat.of().parseHex(ACCESS_LIST_STORAGE_KEY)))),
                    Integers.toBytes(1),
                    HexFormat.of().parseHex("df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479"),
                    HexFormat.of().parseHex("1aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66")));

    // The transactions and the file data are extracted from testnet. The data tests the following scenarios
    // - no call data offloading for legacy, type 1, and type 2
    // - call data offloaded for legacy and type 2
    // - call data inline with call data id in transaction body for legacy

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @SneakyThrows
    public static List<EthereumTransaction> loadEthereumTransactions() {
        var file = ResourceUtils.getFile("classpath:data/ethereumTransaction/ethereum_transaction.json");
        return objectMapper.readValue(
                FileUtils.readFileToString(file, StandardCharsets.UTF_8), new TypeReference<>() {});
    }

    @SneakyThrows
    public static void populateFileData(JdbcOperations jdbcOperations) {
        var file = ResourceUtils.getFile("classpath:data/ethereumTransaction/file_data.sql");
        jdbcOperations.update(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }
}
