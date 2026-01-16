// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ShardID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.parser.record.entity.AbstractEntityRecordItemListenerTest;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntityRecordItemListenerPerformanceCryptoTest extends AbstractEntityRecordItemListenerTest {
    private static final long INITIAL_BALANCE = 1000L;

    private List<RecordItem> insertRecordItemList;
    private List<RecordItem> updateRecordItemList;

    @BeforeAll
    public void setUp() throws Exception {
        int startingAccountNum = 3000; // some hard coded tests accounts exist under this range so start above
        int initialEntityCount = 6000;
        int updateEntityCount = initialEntityCount / 2;
        insertRecordItemList = new ArrayList<>();
        updateRecordItemList = new ArrayList<>();

        // build record list of new crypto accounts
        for (int i = startingAccountNum; i < startingAccountNum + initialEntityCount; i++) {
            insertRecordItemList.add(getCreateAccountRecordItem(i));
        }

        for (int u = startingAccountNum + updateEntityCount; u < startingAccountNum + initialEntityCount; u++) {
            updateRecordItemList.add(getUpdateAccountRecordItem(u));
        }
        for (int c = startingAccountNum + initialEntityCount;
                c < startingAccountNum + initialEntityCount + updateEntityCount;
                c++) {
            updateRecordItemList.add(getCreateAccountRecordItem(c));
        }
    }

    @Test
    @Timeout(4)
    void insertHighCreateEntityCount() {
        parseRecordItemsAndCommit(insertRecordItemList);
        assertThat(entityRepository.findAll()).hasSize(insertRecordItemList.size());
    }

    @Test
    @Timeout(10)
    void insertHighCreateAndUpdateEntityCount() {
        Instant startTime = Instant.now();
        parseRecordItemsAndCommit(insertRecordItemList);
        log.info(
                "Inserting {} entities took {} ms",
                insertRecordItemList.size(),
                java.time.Duration.between(startTime, Instant.now()).getNano() / 1000000);
        assertThat(entityRepository.findAll()).hasSize(insertRecordItemList.size());

        startTime = Instant.now();
        parseRecordItemsAndCommit(updateRecordItemList);
        int updateCount = updateRecordItemList.size() / 2;
        log.info(
                "Inserting {} entities with {} updates took {} ms",
                updateRecordItemList.size(),
                updateCount,
                java.time.Duration.between(startTime, Instant.now()).getNano() / 1000000);
        assertThat(entityRepository.findAll()).hasSize(insertRecordItemList.size() + updateCount);
    }

    private RecordItem getCreateAccountRecordItem(int accountNum) throws Exception {
        Transaction createTransaction = cryptoCreateTransaction();
        TransactionBody createTransactionBody = getTransactionBody(createTransaction);
        TransactionRecord createRecord =
                transactionRecord(createTransactionBody, ResponseCodeEnum.SUCCESS.getNumber(), accountNum);
        return RecordItem.builder()
                .transactionRecord(createRecord)
                .transaction(createTransaction)
                .build();
    }

    private RecordItem getUpdateAccountRecordItem(int accountNum) throws Exception {
        Transaction updateTransaction = cryptoUpdateTransaction(AccountID.newBuilder()
                .setShardNum(0)
                .setRealmNum(0)
                .setAccountNum(accountNum)
                .build());
        TransactionBody updateTransactionBody = getTransactionBody(updateTransaction);
        TransactionRecord createRecord =
                transactionRecord(updateTransactionBody, ResponseCodeEnum.SUCCESS.getNumber(), accountNum);
        return RecordItem.builder()
                .transactionRecord(createRecord)
                .transaction(updateTransaction)
                .build();
    }

    @SuppressWarnings("deprecation")
    private Transaction cryptoCreateTransaction() {
        return buildTransaction(builder -> builder.getCryptoCreateAccountBuilder()
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(1500L))
                .setInitialBalance(INITIAL_BALANCE)
                .setKey(keyFromString(KEY))
                .setMemo("CryptoCreateAccount memo")
                .setNewRealmAdminKey(keyFromString(KEY2))
                .setProxyAccountID(PROXY)
                .setRealmID(RealmID.newBuilder().setShardNum(0).setRealmNum(0).build())
                .setShardID(ShardID.newBuilder().setShardNum(0))
                .setReceiveRecordThreshold(2000L)
                .setReceiverSigRequired(true)
                .setSendRecordThreshold(3000L));
    }

    @SuppressWarnings("deprecation")
    private Transaction cryptoUpdateTransaction(AccountID accountNum) {
        return buildTransaction(builder -> builder.getCryptoUpdateAccountBuilder()
                .setAccountIDToUpdate(accountNum)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(1500L))
                .setExpirationTime(Utility.instantToTimestamp(Instant.now()))
                .setKey(keyFromString(KEY))
                .setMemo(StringValue.of("CryptoUpdateAccount memo"))
                .setProxyAccountID(PROXY_UPDATE)
                .setReceiveRecordThreshold(5001L)
                .setReceiverSigRequired(false)
                .setSendRecordThreshold(6001L));
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, int status, int accountNum) {
        return buildTransactionRecord(
                recordBuilder -> recordBuilder
                        .getReceiptBuilder()
                        .setAccountID(AccountID.newBuilder()
                                .setShardNum(0)
                                .setRealmNum(0)
                                .setAccountNum(accountNum)),
                transactionBody,
                status);
    }
}
