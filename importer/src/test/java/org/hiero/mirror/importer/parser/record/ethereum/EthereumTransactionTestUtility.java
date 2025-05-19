// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.ethereum;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.util.encoders.Hex;
import org.hiero.mirror.common.domain.transaction.EthereumTransaction;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.util.ResourceUtils;

@UtilityClass
public class EthereumTransactionTestUtility {

    // Data used to test type 1 with call data offloaded, copied from services test case
    public static final String RAW_TX_TYPE_1_CALL_DATA = "123456";
    public static final byte[] RAW_TX_TYPE_1 = Hex.decode(
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
                    + "a0abb9e9c510716df2988cf626734ee50dcd9f41d30d638220712b5fe33fe4c816" // r => a0 - 80 = 80 (hex) =
                    // 128 (dec) bytes
                    + "a0249a72e1479b61e00d4f20308577bb63167d71b26138ee5229ca1cb3c49a2e53" // same
            );
    public static final byte[] RAW_TX_TYPE_1_CALL_DATA_OFFLOADED = Hex.decode(
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
                    + "a0abb9e9c510716df2988cf626734ee50dcd9f41d30d638220712b5fe33fe4c816" // r => a0 - 80 = 80 (hex) =
                    // 128 (dec) bytes
                    + "a0249a72e1479b61e00d4f20308577bb63167d71b26138ee5229ca1cb3c49a2e53" // same
            );
    public static final byte[] RAW_TX_TYPE_1_WITH_ACCESS_LIST = Hex.decode(
            "01" // type
                    + "f872" // total length
                    + "82012a" // chain id => 82 - 80 = 2 (hex) = 2 (dec) bytes length
                    + "82160c" // nonce  => same length
                    + "85a54f4c3c00" // gas price => 5 bytes
                    + "832dc6c0" // gas limit => 3 bytes
                    + "94000000000000000000000000000000000000052d" // to => 94 - 80 = 14 (hex) = 20 (dec) bytes
                    + "8502540be400" // value => 5 bytes
                    + "80" // calldata
                    + "c281ff" // access list
                    + "01" // v
                    + "a0abb9e9c510716df2988cf626734ee50dcd9f41d30d638220712b5fe33fe4c816" // r => a0 - 80 = 80 (hex) =
                    // 128 (dec) bytes
                    + "a0249a72e1479b61e00d4f20308577bb63167d71b26138ee5229ca1cb3c49a2e53" // same
            );

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
