// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.reader.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.protobuf.UnknownFieldSet;
import com.hedera.services.stream.proto.AllAccountBalances;
import com.hedera.services.stream.proto.SingleAccountBalances;
import com.hedera.services.stream.proto.TokenUnitBalance;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.AccountBalanceFile;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.exception.InvalidDatasetException;
import org.hiero.mirror.importer.exception.InvalidStreamFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProtoBalanceFileReaderTest {

    private static final String TIMESTAMP = "2021-03-08T20_15_00Z";
    private static final String FILEPATH = Paths.get("data", "accountBalances", "proto", TIMESTAMP + "_Balances.pb.gz")
            .toString();

    private AccountBalanceFile expected;
    private ProtoBalanceFileReader protoBalanceFileReader;
    private StreamFileData streamFileData;

    @BeforeEach
    void setUp() {
        File file = TestUtils.getResource(FILEPATH).toPath().toFile();
        streamFileData = StreamFileData.from(file);
        expected = getExpectedAccountBalanceFile(streamFileData);

        protoBalanceFileReader = new ProtoBalanceFileReader(new CommonDownloaderProperties(new ImporterProperties()));
    }

    @Test
    void readGzip() {
        AccountBalanceFile actual = protoBalanceFileReader.read(streamFileData);
        assertThat(actual)
                .usingRecursiveComparison()
                .ignoringFields("loadStart", "nodeAccountId")
                .isEqualTo(expected);
        assertThat(actual.getLoadStart()).isNotNull().isPositive();
    }

    @Test
    void emptyProtobuf() {
        AllAccountBalances allAccountBalances = AllAccountBalances.newBuilder().build();
        byte[] bytes = allAccountBalances.toByteArray();
        StreamFileData allAccountBalancesStreamFileData = StreamFileData.from(TIMESTAMP + "_Balances.pb", bytes);
        assertThrows(
                InvalidStreamFileException.class, () -> protoBalanceFileReader.read(allAccountBalancesStreamFileData));
    }

    @Test
    void missingTimestamp() {
        AllAccountBalances allAccountBalances = AllAccountBalances.newBuilder()
                .addAllAccounts(SingleAccountBalances.newBuilder().build())
                .build();
        byte[] bytes = allAccountBalances.toByteArray();
        StreamFileData allAccountBalancesStreamFileData = StreamFileData.from(TIMESTAMP + "_Balances.pb", bytes);
        assertThrows(
                InvalidStreamFileException.class, () -> protoBalanceFileReader.read(allAccountBalancesStreamFileData));
    }

    @Test
    void unknownFields() {
        UnknownFieldSet.Field field =
                UnknownFieldSet.Field.newBuilder().addFixed32(11).build();
        AllAccountBalances allAccountBalances = AllAccountBalances.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(1L).build())
                .mergeUnknownFields(
                        UnknownFieldSet.newBuilder().addField(23, field).build())
                .addAllAccounts(SingleAccountBalances.newBuilder().build())
                .build();
        byte[] bytes = allAccountBalances.toByteArray();
        StreamFileData allAccountBalancesStreamFileData = StreamFileData.from(TIMESTAMP + "_Balances.pb", bytes);
        AccountBalanceFile accountBalanceFile = protoBalanceFileReader.read(allAccountBalancesStreamFileData);
        assertThat(accountBalanceFile).isNotNull();
        assertThat(accountBalanceFile.getItems()).hasSize(1);
    }

    @ParameterizedTest(name = "fails whole file on out-of-range entity ids with invalid entity exceptions {0}")
    @ValueSource(booleans = {true, false})
    void readFailsWhenOutOfRangeEntityId(boolean invalidEntityException) {
        final var property = EntityId.INVALID_ENTITY_EXCEPTION_PROPERTY;
        final var previous = System.getProperty(property);
        System.setProperty(property, String.valueOf(invalidEntityException));
        try {
            final long outOfRangeNum = 1L << 38; // above EntityId's 38-bit num range
            // Mix a valid record with out-of-range account/token ids — any bad entry must reject the whole file.
            final var allAccountBalances = AllAccountBalances.newBuilder()
                    .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(1L).build())
                    .addAllAccounts(accountBalance(0, 0, 2000, 3000, 0, 5000))
                    .addAllAccounts(accountBalance(0, 65536, 3, 340, 0, 5000))
                    .addAllAccounts(accountBalance(0, 0, outOfRangeNum, 340, 0, 5000))
                    .addAllAccounts(accountBalance(0, 0, 2001, 3001, 0, outOfRangeNum))
                    .build();
            final var data = StreamFileData.from(TIMESTAMP + "_Balances.pb", allAccountBalances.toByteArray());

            assertThrows(RuntimeException.class, () -> protoBalanceFileReader.read(data));
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    @Test
    void readFailsWhenOnlyAccountIdOutOfRange() {
        // Force the disabled-exception path so toEntityId throws InvalidDatasetException (not
        // InvalidEntityException from EntityId.of), independent of other tests' System properties.
        final var property = EntityId.INVALID_ENTITY_EXCEPTION_PROPERTY;
        final var previous = System.getProperty(property);
        System.setProperty(property, "false");
        try {
            final long outOfRangeNum = 1L << 38;
            final var allAccountBalances = AllAccountBalances.newBuilder()
                    .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(1L).build())
                    .addAllAccounts(accountBalance(0, 0, outOfRangeNum, 340, 0, 5000))
                    .build();
            final var data = StreamFileData.from(TIMESTAMP + "_Balances.pb", allAccountBalances.toByteArray());

            assertThrows(InvalidDatasetException.class, () -> protoBalanceFileReader.read(data));
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    @Test
    void readCorruptedBytes() {
        corrupt(streamFileData.getBytes());

        assertThrows(InvalidStreamFileException.class, () -> protoBalanceFileReader.read(streamFileData));
    }

    @ParameterizedTest(name = "supports {0}")
    @ValueSource(strings = {"2021-03-10T16:00:00Z_Balances.pb.gz", "2021-03-10T16:00:00Z_Balances.pb"})
    void supports(String filename) {
        StreamFileData balancesStreamFileData = StreamFileData.from(filename, new byte[] {1, 2, 3});
        assertThat(protoBalanceFileReader.supports(balancesStreamFileData)).isTrue();
    }

    @ParameterizedTest(name = "does not support {0}")
    @ValueSource(strings = {"2021-03-10T16:00:00Z_Balances.csv", "2021-03-10T16:00:00Z_Balances.csv.gz"})
    void unsupported(String filename) {
        StreamFileData balancesStreamFileData = StreamFileData.from(filename, new byte[] {1, 2, 3});
        assertThat(protoBalanceFileReader.supports(balancesStreamFileData)).isFalse();
    }

    private SingleAccountBalances accountBalance(
            long shard, long realm, long num, long hbarBalance, long tokenRealm, long tokenNum) {
        return SingleAccountBalances.newBuilder()
                .setAccountID(AccountID.newBuilder()
                        .setShardNum(shard)
                        .setRealmNum(realm)
                        .setAccountNum(num))
                .setHbarBalance(hbarBalance)
                .addTokenUnitBalances(TokenUnitBalance.newBuilder()
                        .setTokenId(TokenID.newBuilder().setRealmNum(tokenRealm).setTokenNum(tokenNum))
                        .setBalance(10))
                .build();
    }

    private void corrupt(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (bytes[i] ^ 0xff);
        }
    }

    private AccountBalanceFile getExpectedAccountBalanceFile(StreamFileData streamFileData) {
        Instant instant = Instant.parse(TIMESTAMP.replace("_", ":"));
        long consensusTimestamp = DomainUtils.convertToNanosMax(instant);

        long accountNum = 2000;
        long hbarBalance = 3000;
        long tokenNum = 5000;
        long tokenBalance = 6000;

        List<AccountBalance> accountBalances = IntStream.range(0, 10)
                .mapToObj(i -> {
                    EntityId accountId = EntityId.of(0, 0, accountNum + i);
                    List<TokenBalance> tokenBalances = IntStream.range(0, 5)
                            .mapToObj(j -> {
                                EntityId tokenId = EntityId.of(0, 0, tokenNum + i * 5L + j);
                                return new TokenBalance(
                                        tokenBalance + i * 5L + j,
                                        new TokenBalance.Id(consensusTimestamp, accountId, tokenId));
                            })
                            .toList();
                    return new AccountBalance(
                            hbarBalance + i, tokenBalances, new AccountBalance.Id(consensusTimestamp, accountId));
                })
                .toList();
        return AccountBalanceFile.builder()
                .bytes(streamFileData.getBytes())
                .consensusTimestamp(consensusTimestamp)
                .fileHash(
                        "67c2fd054621366dd5a37b6ee36a51bc590361379d539fdac2265af08cb8097729218c7d9ff1f1e354c85b820c5b8cf8")
                .items(accountBalances)
                .name(streamFileData.getFilename())
                .build();
    }
}
