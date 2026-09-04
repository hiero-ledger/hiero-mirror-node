// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.Prng;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.importer.repository.PrngRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.util.Version;

@RequiredArgsConstructor
class EntityRecordItemListenerUtilTest extends AbstractEntityRecordItemListenerTest {

    private final PrngRepository prngRepository;

    @Test
    void prngNumber() {
        var recordItem = recordItemBuilder.prng(Integer.MAX_VALUE).build();
        int pseudorandomNumber = recordItem.getTransactionRecord().getPrngNumber();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, prngRepository.count()),
                () -> assertThat(prngRepository.findAll())
                        .first()
                        .isNotNull()
                        .returns(recordItem.getConsensusTimestamp(), Prng::getConsensusTimestamp)
                        .returns(Integer.MAX_VALUE, Prng::getRange)
                        .returns(pseudorandomNumber, Prng::getPrngNumber)
                        .returns(null, Prng::getPrngBytes));
    }

    @Test
    void prngBytes() {
        var recordItem = recordItemBuilder.prng(0).build();
        byte[] pseudorandomBytes =
                recordItem.getTransactionRecord().getPrngBytes().toByteArray();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(1, prngRepository.count()),
                () -> assertThat(prngRepository.findAll())
                        .first()
                        .isNotNull()
                        .returns(recordItem.getConsensusTimestamp(), Prng::getConsensusTimestamp)
                        .returns(0, Prng::getRange)
                        .returns(pseudorandomBytes, Prng::getPrngBytes)
                        .returns(null, Prng::getPrngNumber));
    }

    /**
     * This test writes a TransactionBody that contains an invalid transaction body without unknown fields or a valid
     * transaction body and verifies it is still inserted into the database.
     * <p>
     * See issue 4843
     */
    @Test
    void invalidPrngTransaction() {
        byte[] invalidBytes = new byte[] {
            10, 23, 10, 21, 10, 11, 8, -23, -105, -78, -101, 6, 16, -115, -95, -56, 47, 18, 4, 24, -108, -74, 85, 32, 1
        };
        var transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(ByteString.copyFrom(invalidBytes))
                .build();

        var recordItem = RecordItem.builder()
                .hapiVersion(new Version(0, 30, 0))
                .transactionRecord(TransactionRecord.newBuilder().build())
                .transaction(transaction)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertAll(
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(0, prngRepository.count()),
                () -> assertThat(transactionRepository.findAll())
                        .hasSize(1)
                        .first()
                        .extracting(org.hiero.mirror.common.domain.transaction.Transaction::getType)
                        .isEqualTo(TransactionBody.DataCase.DATA_NOT_SET.getNumber()));
    }

    @Test
    void unknownTransactionTypeOutOfSmallintRange() {
        final var persistProperties = entityProperties.getPersist();
        final var backupEntityTransactionsValue = persistProperties.isEntityTransactions();
        persistProperties.setEntityTransactions(true);
        try {
            final var recordItem = recordItemWithUnknownType(Short.MAX_VALUE + 1);

            parseRecordItemAndCommit(recordItem);

            assertThat(transactionRepository.findAll())
                    .hasSize(1)
                    .first()
                    .extracting(org.hiero.mirror.common.domain.transaction.Transaction::getType)
                    .isEqualTo(TransactionBody.DataCase.DATA_NOT_SET.getNumber());
            assertThat(entityTransactionRepository.findAll()).isNotEmpty().allSatisfy(entityTransaction -> assertThat(
                            entityTransaction.getType())
                    .isEqualTo(TransactionBody.DataCase.DATA_NOT_SET.getNumber()));
        } finally {
            persistProperties.setEntityTransactions(backupEntityTransactionsValue);
        }
    }

    @Test
    void transactionResultOutOfSmallintRange() {
        final var persistProperties = entityProperties.getPersist();
        final var backupEntityTransactionsValue = persistProperties.isEntityTransactions();
        persistProperties.setEntityTransactions(true);
        try {
            final var recordItem = recordItemBuilder
                    .cryptoTransfer()
                    .receipt(r -> r.setStatusValue(Short.MAX_VALUE + 1))
                    .build();

            parseRecordItemAndCommit(recordItem);

            assertThat(transactionRepository.findAll())
                    .hasSize(1)
                    .first()
                    .extracting(org.hiero.mirror.common.domain.transaction.Transaction::getResult)
                    .isEqualTo((int) Short.MAX_VALUE);
            assertThat(entityTransactionRepository.findAll()).isNotEmpty().allSatisfy(entityTransaction -> assertThat(
                            entityTransaction.getResult())
                    .isEqualTo(Short.MAX_VALUE));
        } finally {
            persistProperties.setEntityTransactions(backupEntityTransactionsValue);
        }
    }

    private RecordItem recordItemWithUnknownType(int fieldNumber) {
        final var field = UnknownFieldSet.Field.newBuilder()
                .addLengthDelimited(ByteString.copyFromUtf8("foo"))
                .build();
        final var transactionId = TransactionID.newBuilder()
                .setAccountID(PAYER)
                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(1))
                .build();
        final var body = TransactionBody.newBuilder()
                .setTransactionID(transactionId)
                .setNodeAccountID(NODE)
                .setMemo("memo")
                .setUnknownFields(UnknownFieldSet.newBuilder()
                        .addField(fieldNumber, field)
                        .build())
                .build();
        final var transaction =
                Transaction.newBuilder().setBodyBytes(body.toByteString()).build();
        final var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(1).setNanos(1))
                .setReceipt(TransactionReceipt.newBuilder().setStatus(ResponseCodeEnum.SUCCESS))
                .setTransactionID(transactionId)
                .build();
        return RecordItem.builder()
                .hapiVersion(new Version(0, 30, 0))
                .transactionRecord(transactionRecord)
                .transaction(transaction)
                .build();
    }
}
