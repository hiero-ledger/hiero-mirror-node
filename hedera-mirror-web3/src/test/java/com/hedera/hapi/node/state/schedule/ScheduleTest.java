// SPDX-License-Identifier: Apache-2.0

package com.hedera.hapi.node.state.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleTest {
    private static final ScheduleID TEST_SCHEDULE_ID =
            ScheduleID.newBuilder().scheduleNum(12345).build();
    private static final AccountID TEST_SCHEDULER_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(12345).build();
    private static final AccountID TEST_PAYER_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(54321).build();
    private static final Key TEST_ADMIN_KEY =
            Key.newBuilder().ed25519(Bytes.wrap(new byte[32])).build();
    private static final Timestamp TEST_SCHEDULE_VALID_START =
            Timestamp.newBuilder().seconds(1234567890).build();
    private static final long TEST_PROVIDED_EXPIRATION_SECOND = 1234567890L;
    private static final long TEST_CALCULATED_EXPIRATION_SECOND = 1234567890L;
    private static final Timestamp TEST_RESOLUTION_TIME =
            Timestamp.newBuilder().seconds(1234567890).build();
    private static final SchedulableTransactionBody TEST_SCHEDULED_TRANSACTION =
            SchedulableTransactionBody.newBuilder().build();
    private static final TransactionBody TEST_ORIGINAL_CREATE_TRANSACTION =
            TransactionBody.newBuilder().build();
    private static final List<Key> TEST_SIGNATORIES = Collections.emptyList();

    private Schedule subject;

    @BeforeEach
    void setUp() {
        subject = new Schedule(
                TEST_SCHEDULE_ID,
                false,
                false,
                false,
                "test memo",
                TEST_SCHEDULER_ACCOUNT_ID,
                TEST_PAYER_ACCOUNT_ID,
                TEST_ADMIN_KEY,
                TEST_SCHEDULE_VALID_START,
                TEST_PROVIDED_EXPIRATION_SECOND,
                TEST_CALCULATED_EXPIRATION_SECOND,
                TEST_RESOLUTION_TIME,
                TEST_SCHEDULED_TRANSACTION,
                TEST_ORIGINAL_CREATE_TRANSACTION,
                TEST_SIGNATORIES);
    }

    @Test
    void objectCreationWorks() {
        assertEquals(TEST_SCHEDULE_ID, subject.scheduleId());
        assertFalse(subject.deleted());
        assertFalse(subject.executed());
        assertFalse(subject.waitForExpiry());
        assertEquals("test memo", subject.memo());
        assertEquals(TEST_SCHEDULER_ACCOUNT_ID, subject.schedulerAccountId());
        assertEquals(TEST_PAYER_ACCOUNT_ID, subject.payerAccountId());
        assertEquals(TEST_ADMIN_KEY, subject.adminKey());
        assertEquals(TEST_SCHEDULE_VALID_START, subject.scheduleValidStart());
        assertEquals(TEST_PROVIDED_EXPIRATION_SECOND, subject.providedExpirationSecond());
        assertEquals(TEST_CALCULATED_EXPIRATION_SECOND, subject.calculatedExpirationSecond());
        assertEquals(TEST_RESOLUTION_TIME, subject.resolutionTime());
        assertEquals(TEST_SCHEDULED_TRANSACTION, subject.scheduledTransaction());
        assertEquals(TEST_ORIGINAL_CREATE_TRANSACTION, subject.originalCreateTransaction());
        assertEquals(TEST_SIGNATORIES, subject.signatoriesSupplier().get());
    }

    @Test
    void builderWorks() {
        final var built = Schedule.newBuilder()
                .scheduleId(TEST_SCHEDULE_ID)
                .deleted(false)
                .executed(false)
                .waitForExpiry(false)
                .memo("test memo")
                .schedulerAccountId(TEST_SCHEDULER_ACCOUNT_ID)
                .payerAccountId(TEST_PAYER_ACCOUNT_ID)
                .adminKey(TEST_ADMIN_KEY)
                .scheduleValidStart(TEST_SCHEDULE_VALID_START)
                .providedExpirationSecond(TEST_PROVIDED_EXPIRATION_SECOND)
                .calculatedExpirationSecond(TEST_CALCULATED_EXPIRATION_SECOND)
                .resolutionTime(TEST_RESOLUTION_TIME)
                .scheduledTransaction(TEST_SCHEDULED_TRANSACTION)
                .originalCreateTransaction(TEST_ORIGINAL_CREATE_TRANSACTION)
                .signatories(TEST_SIGNATORIES)
                .build();

        assertThat(built).isEqualTo(subject);
    }

    @Test
    void defaultInstanceWorks() {
        final var defaultInstance = Schedule.DEFAULT;
        assertThat(defaultInstance.scheduleId()).isNull();
        assertThat(defaultInstance.deleted()).isFalse();
        assertThat(defaultInstance.executed()).isFalse();
        assertThat(defaultInstance.waitForExpiry()).isFalse();
        assertThat(defaultInstance.memo()).isEmpty();
        assertThat(defaultInstance.schedulerAccountId()).isNull();
        assertThat(defaultInstance.payerAccountId()).isNull();
        assertThat(defaultInstance.adminKey()).isNull();
        assertThat(defaultInstance.scheduleValidStart()).isNull();
        assertThat(defaultInstance.providedExpirationSecond()).isZero();
        assertThat(defaultInstance.calculatedExpirationSecond()).isZero();
        assertThat(defaultInstance.resolutionTime()).isNull();
        assertThat(defaultInstance.scheduledTransaction()).isNull();
        assertThat(defaultInstance.originalCreateTransaction()).isNull();
        assertThat(defaultInstance.signatoriesSupplier().get()).isEmpty();
    }

    @Test
    void testEqualsWithNull() {
        assertNotEquals(null, subject);
    }

    @Test
    void testEqualsWithSameObject() {
        assertEquals(subject, subject);
        assertEquals(subject.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithDifferentClass() {
        assertNotEquals("not a schedule", subject);
    }

    @Test
    void testEqualsWithDifferentScheduleId() {
        final var differentScheduleId = subject.copyBuilder()
                .scheduleId(ScheduleID.newBuilder()
                        .shardNum(1)
                        .realmNum(1)
                        .scheduleNum(54321)
                        .build())
                .build();

        assertNotEquals(differentScheduleId, subject);
        assertNotEquals(subject, differentScheduleId);
        assertNotEquals(differentScheduleId.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithNullScheduleId() {
        final var nullScheduleId =
                subject.copyBuilder().scheduleId((ScheduleID) null).build();

        assertNotEquals(nullScheduleId, subject);
        assertNotEquals(subject, nullScheduleId);
        assertNotEquals(nullScheduleId.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithDifferentDeleted() {
        final var differentDeleted = subject.copyBuilder().deleted(true).build();

        assertNotEquals(differentDeleted, subject);
        assertNotEquals(subject, differentDeleted);
        assertNotEquals(differentDeleted.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithDifferentExecuted() {
        final var differentExecuted = subject.copyBuilder().executed(true).build();

        assertNotEquals(differentExecuted, subject);
        assertNotEquals(subject, differentExecuted);
        assertNotEquals(differentExecuted.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithDifferentWaitForExpiry() {
        final var differentWaitForExpiry =
                subject.copyBuilder().waitForExpiry(true).build();

        assertNotEquals(differentWaitForExpiry, subject);
        assertNotEquals(subject, differentWaitForExpiry);
        assertNotEquals(differentWaitForExpiry.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithDifferentMemo() {
        final var differentMemo = subject.copyBuilder().memo("different memo").build();

        assertNotEquals(differentMemo, subject);
        assertNotEquals(subject, differentMemo);
        assertNotEquals(differentMemo.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithNullMemo() {
        final var nullMemo = subject.copyBuilder().memo(null).build();

        assertNotEquals(nullMemo, subject);
        assertNotEquals(subject, nullMemo);
        assertNotEquals(nullMemo.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithDifferentSchedulerAccountId() {
        final var differentSchedulerAccountId = subject.copyBuilder()
                .schedulerAccountId(AccountID.newBuilder()
                        .shardNum(1)
                        .realmNum(1)
                        .accountNum(54321)
                        .build())
                .build();

        assertNotEquals(differentSchedulerAccountId, subject);
        assertNotEquals(subject, differentSchedulerAccountId);
        assertNotEquals(differentSchedulerAccountId.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithNullSchedulerAccountId() {
        final var nullSchedulerAccountId =
                subject.copyBuilder().schedulerAccountId((AccountID) null).build();

        assertNotEquals(nullSchedulerAccountId, subject);
        assertNotEquals(subject, nullSchedulerAccountId);
        assertNotEquals(nullSchedulerAccountId.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithDifferentPayerAccountId() {
        final var differentPayerAccountId = subject.copyBuilder()
                .payerAccountId(AccountID.newBuilder()
                        .shardNum(1)
                        .realmNum(1)
                        .accountNum(12345)
                        .build())
                .build();

        assertNotEquals(differentPayerAccountId, subject);
        assertNotEquals(subject, differentPayerAccountId);
        assertNotEquals(differentPayerAccountId.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithNullPayerAccountId() {
        final var nullPayerAccountId =
                subject.copyBuilder().payerAccountId((AccountID) null).build();

        assertNotEquals(nullPayerAccountId, subject);
        assertNotEquals(subject, nullPayerAccountId);
        assertNotEquals(nullPayerAccountId.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithDifferentAdminKey() {
        final var differentAdminKey = subject.copyBuilder()
                .adminKey(Key.newBuilder()
                        .ecdsaSecp256k1(Bytes.wrap(new byte[32]))
                        .build())
                .build();

        assertNotEquals(differentAdminKey, subject);
        assertNotEquals(subject, differentAdminKey);
        assertNotEquals(differentAdminKey.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithNullAdminKey() {
        final var nullAdminKey = subject.copyBuilder().adminKey((Key) null).build();

        assertNotEquals(nullAdminKey, subject);
        assertNotEquals(subject, nullAdminKey);
        assertNotEquals(nullAdminKey.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithDifferentScheduleValidStart() {
        final var differentScheduleValidStart = subject.copyBuilder()
                .scheduleValidStart(Timestamp.newBuilder().seconds(987654321).build())
                .build();

        assertNotEquals(differentScheduleValidStart, subject);
        assertNotEquals(subject, differentScheduleValidStart);
        assertNotEquals(differentScheduleValidStart.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithNullScheduleValidStart() {
        final var nullScheduleValidStart =
                subject.copyBuilder().scheduleValidStart((Timestamp) null).build();

        assertNotEquals(nullScheduleValidStart, subject);
        assertNotEquals(subject, nullScheduleValidStart);
        assertNotEquals(nullScheduleValidStart.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithDifferentProvidedExpirationSecond() {
        final var differentProvidedExpirationSecond =
                subject.copyBuilder().providedExpirationSecond(987654321L).build();

        assertNotEquals(differentProvidedExpirationSecond, subject);
        assertNotEquals(subject, differentProvidedExpirationSecond);
        assertNotEquals(differentProvidedExpirationSecond.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithDifferentCalculatedExpirationSecond() {
        final var differentCalculatedExpirationSecond =
                subject.copyBuilder().calculatedExpirationSecond(987654321L).build();

        assertNotEquals(differentCalculatedExpirationSecond, subject);
        assertNotEquals(subject, differentCalculatedExpirationSecond);
        assertNotEquals(differentCalculatedExpirationSecond.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithDifferentResolutionTime() {
        final var differentResolutionTime = subject.copyBuilder()
                .resolutionTime(Timestamp.newBuilder().seconds(987654321).build())
                .build();

        assertNotEquals(differentResolutionTime, subject);
        assertNotEquals(subject, differentResolutionTime);
        assertNotEquals(differentResolutionTime.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithNullResolutionTime() {
        final var nullResolutionTime =
                subject.copyBuilder().resolutionTime((Timestamp) null).build();

        assertNotEquals(nullResolutionTime, subject);
        assertNotEquals(subject, nullResolutionTime);
        assertNotEquals(nullResolutionTime.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithDifferentScheduledTransaction() {
        final var differentScheduledTransaction = subject.copyBuilder()
                .scheduledTransaction(SchedulableTransactionBody.newBuilder()
                        .memo("different memo")
                        .build())
                .build();

        assertNotEquals(differentScheduledTransaction, subject);
        assertNotEquals(subject, differentScheduledTransaction);
        assertNotEquals(differentScheduledTransaction.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithNullScheduledTransaction() {
        final var nullScheduledTransaction = subject.copyBuilder()
                .scheduledTransaction((SchedulableTransactionBody) null)
                .build();

        assertNotEquals(nullScheduledTransaction, subject);
        assertNotEquals(subject, nullScheduledTransaction);
        assertNotEquals(nullScheduledTransaction.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithDifferentOriginalCreateTransaction() {
        final var differentOriginalCreateTransaction = subject.copyBuilder()
                .originalCreateTransaction(TransactionBody.newBuilder()
                        .memo("different transaction")
                        .build())
                .build();

        assertNotEquals(differentOriginalCreateTransaction, subject);
        assertNotEquals(subject, differentOriginalCreateTransaction);
        assertNotEquals(differentOriginalCreateTransaction.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithNullOriginalCreateTransaction() {
        final var nullOriginalCreateTransaction = subject.copyBuilder()
                .originalCreateTransaction((TransactionBody) null)
                .build();

        assertNotEquals(nullOriginalCreateTransaction, subject);
        assertNotEquals(subject, nullOriginalCreateTransaction);
        assertNotEquals(nullOriginalCreateTransaction.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithDifferentSignatories() {
        final var differentSignatories = subject.copyBuilder()
                .signatories(List.of(
                        Key.newBuilder().ed25519(Bytes.wrap(new byte[32])).build()))
                .build();

        assertNotEquals(differentSignatories, subject);
        assertNotEquals(subject, differentSignatories);
        assertNotEquals(differentSignatories.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithSameSignatoriesEmpty() {
        final var nullSignatories =
                subject.copyBuilder().signatories((List<Key>) null).build();

        assertEquals(nullSignatories, subject);
        assertEquals(subject, nullSignatories);
        assertEquals(nullSignatories.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithSameSignatoriesNonEmpty() {
        final var signatories = List.of(
                Key.newBuilder().ecdsaSecp256k1(Bytes.wrap(new byte[32])).build());

        subject = subject.copyBuilder().signatories(signatories).build();
        final var subject2 = subject.copyBuilder().signatories(signatories).build();

        assertEquals(subject2, subject);
        assertEquals(subject, subject2);
        assertEquals(subject2.hashCode(), subject.hashCode());
    }

    @Test
    void testEqualsWithIdenticalObject() {
        final var identical = subject.copyBuilder().build();
        assertEquals(identical, subject);
        assertEquals(subject, identical);
        assertEquals(identical.hashCode(), subject.hashCode());
    }

    @Test
    void testBuilderWithVarargsSignatories() {
        final var key1 = Key.newBuilder().ed25519(Bytes.wrap(new byte[32])).build();
        final var key2 =
                Key.newBuilder().ecdsaSecp256k1(Bytes.wrap(new byte[32])).build();
        final var schedule = Schedule.newBuilder().signatories(key1, key2).build();

        assertEquals(List.of(key1, key2), schedule.signatories());
    }

    @Test
    void testBuilderWithNullSignatories() {
        final var schedule =
                Schedule.newBuilder().signatories((Supplier<List<Key>>) null).build();

        assertEquals(Collections.EMPTY_LIST, schedule.signatories());
    }

    @Test
    void testBuilderWithSupplierSignatories() {
        final var key1 = Key.newBuilder().ed25519(Bytes.wrap(new byte[32])).build();
        final var key2 =
                Key.newBuilder().ecdsaSecp256k1(Bytes.wrap(new byte[32])).build();
        final var signatories = List.of(key1, key2);
        final var schedule =
                Schedule.newBuilder().signatories(() -> signatories).build();

        assertEquals(signatories, schedule.signatories());
    }

    @Test
    void testBuilderWithTransactionBodyBuilder() {
        final var transactionBody =
                TransactionBody.newBuilder().memo("test memo").build();
        final var schedule = Schedule.newBuilder()
                .originalCreateTransaction(TransactionBody.newBuilder().memo("test memo"))
                .build();

        assertEquals(transactionBody, schedule.originalCreateTransaction());
    }

    @Test
    void testBuilderWithSchedulableTransactionBodyBuilder() {
        final var schedulableTransactionBody =
                SchedulableTransactionBody.newBuilder().memo("test memo").build();
        final var schedule = Schedule.newBuilder()
                .scheduledTransaction(SchedulableTransactionBody.newBuilder().memo("test memo"))
                .build();

        assertEquals(schedulableTransactionBody, schedule.scheduledTransaction());
    }

    @Test
    void testBuilderWithTimestampBuilders() {
        final long seconds = 1234567890;
        final var timestamp = Timestamp.newBuilder().seconds(seconds).build();

        final var schedule = Schedule.newBuilder()
                .resolutionTime(Timestamp.newBuilder().seconds(seconds))
                .scheduleValidStart(Timestamp.newBuilder().seconds(seconds))
                .build();

        assertEquals(timestamp, schedule.resolutionTime());
        assertEquals(timestamp, schedule.scheduleValidStart());
    }

    @Test
    void testBuilderWithKeyBuilder() {
        final var key = Key.newBuilder().ed25519(Bytes.wrap(new byte[32])).build();
        final var schedule = Schedule.newBuilder()
                .adminKey(Key.newBuilder().ed25519(Bytes.wrap(new byte[32])))
                .build();

        assertEquals(key, schedule.adminKey());
    }

    @Test
    void testBuilderWithAccountIDBuilders() {
        final long accountNum = 12345;
        final var accountId = AccountID.newBuilder().accountNum(accountNum).build();
        final var schedule = Schedule.newBuilder()
                .payerAccountId(AccountID.newBuilder().accountNum(accountNum))
                .schedulerAccountId(AccountID.newBuilder().accountNum(accountNum))
                .build();

        assertEquals(accountId, schedule.payerAccountId());
        assertEquals(accountId, schedule.schedulerAccountId());
    }

    @Test
    void testBuilderWithScheduleIDBuilder() {
        final var scheduleNum = 12345;
        final var scheduleId = ScheduleID.newBuilder().scheduleNum(scheduleNum).build();
        final var schedule = Schedule.newBuilder()
                .scheduleId(ScheduleID.newBuilder().scheduleNum(scheduleNum))
                .build();

        assertEquals(scheduleId, schedule.scheduleId());
    }

    @Test
    void testHasOriginalCreateTransaction() {
        final var schedule = Schedule.newBuilder()
                .originalCreateTransaction(
                        TransactionBody.newBuilder().memo("test").build())
                .build();
        assertTrue(schedule.hasOriginalCreateTransaction());

        final var emptySchedule = Schedule.newBuilder().build();
        assertFalse(emptySchedule.hasOriginalCreateTransaction());
    }

    @Test
    void testHasScheduledTransaction() {
        final var schedule = Schedule.newBuilder()
                .scheduledTransaction(
                        SchedulableTransactionBody.newBuilder().memo("test").build())
                .build();
        assertTrue(schedule.hasScheduledTransaction());

        final var emptySchedule = Schedule.newBuilder().build();
        assertFalse(emptySchedule.hasScheduledTransaction());
    }

    @Test
    void testHasResolutionTime() {
        final var schedule = Schedule.newBuilder()
                .resolutionTime(Timestamp.newBuilder().seconds(1234567890).build())
                .build();
        assertTrue(schedule.hasResolutionTime());

        final var emptySchedule = Schedule.newBuilder().build();
        assertFalse(emptySchedule.hasResolutionTime());
    }

    @Test
    void testHasScheduleValidStart() {
        final var schedule = Schedule.newBuilder()
                .scheduleValidStart(Timestamp.newBuilder().seconds(1234567890).build())
                .build();
        assertTrue(schedule.hasScheduleValidStart());

        final var emptySchedule = Schedule.newBuilder().build();
        assertFalse(emptySchedule.hasScheduleValidStart());
    }

    @Test
    void testHasAdminKey() {
        final var schedule = Schedule.newBuilder()
                .adminKey(Key.newBuilder().ed25519(Bytes.wrap(new byte[32])).build())
                .build();
        assertTrue(schedule.hasAdminKey());

        final var emptySchedule = Schedule.newBuilder().build();
        assertFalse(emptySchedule.hasAdminKey());
    }

    @Test
    void testIfOriginalCreateTransaction() {
        final var transaction = TransactionBody.newBuilder().memo("test").build();
        final var schedule =
                Schedule.newBuilder().originalCreateTransaction(transaction).build();

        final var capturedTransaction = new Object() {
            TransactionBody value;
        };
        schedule.ifOriginalCreateTransaction(t -> capturedTransaction.value = t);
        assertEquals(transaction, capturedTransaction.value);

        final var emptySchedule = Schedule.newBuilder().build();
        emptySchedule.ifOriginalCreateTransaction(t -> fail("Should not be called"));
    }

    @Test
    void testIfScheduledTransaction() {
        final var scheduledTransaction =
                SchedulableTransactionBody.newBuilder().memo("test").build();
        final var schedule =
                Schedule.newBuilder().scheduledTransaction(scheduledTransaction).build();

        final var capturedTransaction = new Object() {
            SchedulableTransactionBody value;
        };
        schedule.ifScheduledTransaction(t -> capturedTransaction.value = t);
        assertEquals(scheduledTransaction, capturedTransaction.value);

        final var emptySchedule = Schedule.newBuilder().build();
        emptySchedule.ifScheduledTransaction(t -> fail("Should not be called"));
    }

    @Test
    void testIfResolutionTime() {
        final var timestamp = Timestamp.newBuilder().seconds(1234567890).build();
        final var schedule = Schedule.newBuilder().resolutionTime(timestamp).build();

        final var capturedTimestamp = new Object() {
            Timestamp value;
        };
        schedule.ifResolutionTime(t -> capturedTimestamp.value = t);
        assertEquals(timestamp, capturedTimestamp.value);

        final var emptySchedule = Schedule.newBuilder().build();
        emptySchedule.ifResolutionTime(t -> fail("Should not be called"));
    }

    @Test
    void testIfScheduleValidStart() {
        final var timestamp = Timestamp.newBuilder().seconds(1234567890).build();
        final var schedule = Schedule.newBuilder().scheduleValidStart(timestamp).build();

        final var capturedTimestamp = new Object() {
            Timestamp value;
        };
        schedule.ifScheduleValidStart(t -> capturedTimestamp.value = t);
        assertEquals(timestamp, capturedTimestamp.value);

        final var emptySchedule = Schedule.newBuilder().build();
        emptySchedule.ifScheduleValidStart(t -> fail("Should not be called"));
    }

    @Test
    void testIfAdminKey() {
        final var key = Key.newBuilder().ed25519(Bytes.wrap(new byte[32])).build();
        final var schedule = Schedule.newBuilder().adminKey(key).build();

        final var capturedKey = new Object() {
            Key value;
        };
        schedule.ifAdminKey(k -> capturedKey.value = k);
        assertEquals(key, capturedKey.value);

        final var emptySchedule = Schedule.newBuilder().build();
        emptySchedule.ifAdminKey(k -> fail("Should not be called"));
    }

    @Test
    void testOriginalCreateTransactionOrThrow() {
        final var transaction = TransactionBody.newBuilder().memo("test").build();
        final var schedule =
                Schedule.newBuilder().originalCreateTransaction(transaction).build();
        assertEquals(transaction, schedule.originalCreateTransactionOrThrow());

        final var emptySchedule = Schedule.newBuilder().build();
        assertThrows(NullPointerException.class, emptySchedule::originalCreateTransactionOrThrow);
    }

    @Test
    void testScheduledTransactionOrThrow() {
        final var transaction =
                SchedulableTransactionBody.newBuilder().memo("test").build();
        final var schedule =
                Schedule.newBuilder().scheduledTransaction(transaction).build();
        assertEquals(transaction, schedule.scheduledTransactionOrThrow());

        final var emptySchedule = Schedule.newBuilder().build();
        assertThrows(NullPointerException.class, emptySchedule::scheduledTransactionOrThrow);
    }

    @Test
    void testResolutionTimeOrThrow() {
        final var timestamp = Timestamp.newBuilder().seconds(1234567890).build();
        final var schedule = Schedule.newBuilder().resolutionTime(timestamp).build();
        assertEquals(timestamp, schedule.resolutionTimeOrThrow());

        final var emptySchedule = Schedule.newBuilder().build();
        assertThrows(NullPointerException.class, emptySchedule::resolutionTimeOrThrow);
    }

    @Test
    void testScheduleValidStartOrThrow() {
        final var timestamp = Timestamp.newBuilder().seconds(1234567890).build();
        final var schedule = Schedule.newBuilder().scheduleValidStart(timestamp).build();
        assertEquals(timestamp, schedule.scheduleValidStartOrThrow());

        final var emptySchedule = Schedule.newBuilder().build();
        assertThrows(NullPointerException.class, emptySchedule::scheduleValidStartOrThrow);
    }

    @Test
    void testAdminKeyOrThrow() {
        final var key = Key.newBuilder().ed25519(Bytes.wrap(new byte[32])).build();
        final var schedule = Schedule.newBuilder().adminKey(key).build();
        assertEquals(key, schedule.adminKeyOrThrow());

        final var emptySchedule = Schedule.newBuilder().build();
        assertThrows(NullPointerException.class, emptySchedule::adminKeyOrThrow);
    }

    @Test
    void testOriginalCreateTransactionOrElse() {
        final var transaction = TransactionBody.newBuilder().memo("test").build();
        final var defaultTransaction =
                TransactionBody.newBuilder().memo("default").build();
        final var schedule =
                Schedule.newBuilder().originalCreateTransaction(transaction).build();
        assertEquals(transaction, schedule.originalCreateTransactionOrElse(defaultTransaction));

        final var emptySchedule = Schedule.newBuilder().build();
        assertEquals(defaultTransaction, emptySchedule.originalCreateTransactionOrElse(defaultTransaction));
    }

    @Test
    void testScheduledTransactionOrElse() {
        final var transaction =
                SchedulableTransactionBody.newBuilder().memo("test").build();
        final var defaultTransaction =
                SchedulableTransactionBody.newBuilder().memo("default").build();
        final var schedule =
                Schedule.newBuilder().scheduledTransaction(transaction).build();
        assertEquals(transaction, schedule.scheduledTransactionOrElse(defaultTransaction));

        final var emptySchedule = Schedule.newBuilder().build();
        assertEquals(defaultTransaction, emptySchedule.scheduledTransactionOrElse(defaultTransaction));
    }

    @Test
    void testResolutionTimeOrElse() {
        final var timestamp = Timestamp.newBuilder().seconds(1234567890).build();
        final var defaultTimestamp = Timestamp.newBuilder().seconds(987654321).build();
        final var schedule = Schedule.newBuilder().resolutionTime(timestamp).build();
        assertEquals(timestamp, schedule.resolutionTimeOrElse(defaultTimestamp));

        final var emptySchedule = Schedule.newBuilder().build();
        assertEquals(defaultTimestamp, emptySchedule.resolutionTimeOrElse(defaultTimestamp));
    }

    @Test
    void testScheduleValidStartOrElse() {
        final var timestamp = Timestamp.newBuilder().seconds(1234567890).build();
        final var defaultTimestamp = Timestamp.newBuilder().seconds(987654321).build();
        final var schedule = Schedule.newBuilder().scheduleValidStart(timestamp).build();
        assertEquals(timestamp, schedule.scheduleValidStartOrElse(defaultTimestamp));

        final var emptySchedule = Schedule.newBuilder().build();
        assertEquals(defaultTimestamp, emptySchedule.scheduleValidStartOrElse(defaultTimestamp));
    }

    @Test
    void testAdminKeyOrElse() {
        final var key = Key.newBuilder().ed25519(Bytes.wrap(new byte[32])).build();
        final var defaultKey =
                Key.newBuilder().ecdsaSecp256k1(Bytes.wrap(new byte[32])).build();
        final var schedule = Schedule.newBuilder().adminKey(key).build();
        assertEquals(key, schedule.adminKeyOrElse(defaultKey));

        final var emptySchedule = Schedule.newBuilder().build();
        assertEquals(defaultKey, emptySchedule.adminKeyOrElse(defaultKey));
    }
}
