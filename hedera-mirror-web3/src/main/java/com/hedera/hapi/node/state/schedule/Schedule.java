// SPDX-License-Identifier: Apache-2.0

package com.hedera.hapi.node.state.schedule;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.JsonCodec;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Representation of a Hedera Schedule entry in the network Merkle tree.<br/>
 * A Schedule represents a request to run a transaction _at some future time_
 * either when the `Schedule` expires (if long term schedules are enabled and
 * `wait_for_expiry` is true) or as soon as the `Schedule` has gathered
 * enough signatures via any combination of the `scheduleCreate` and 0 or more
 * subsequent `scheduleSign` transactions.
 *
 * @param scheduleId <b>(1)</b> This schedule's ID within the global network state.
 *                   <p>
 *                   This value SHALL be unique within the network.
 * @param deleted <b>(2)</b> A flag indicating this schedule is deleted.
 *                <p>
 *                A schedule SHALL either be executed or deleted, but never both.
 * @param executed <b>(3)</b> A flag indicating this schedule has executed.
 *                 <p>
 *                 A schedule SHALL either be executed or deleted, but never both.
 * @param waitForExpiry <b>(4)</b> A schedule flag to wait for expiration before executing.
 *                      <p>
 *                      A schedule SHALL be executed immediately when all necessary signatures
 *                      are gathered, unless this flag is set.<br/>
 *                      If this flag is set, the schedule SHALL wait until the consensus time
 *                      reaches `expiration_time_provided`, when signatures MUST again be
 *                      verified. If all required signatures are present at that time, the
 *                      schedule SHALL be executed. Otherwise the schedule SHALL expire without
 *                      execution.
 *                      <p>
 *                      Note that a schedule is always removed from state after it expires,
 *                      regardless of whether it was executed or not.
 * @param memo <b>(5)</b> A short description for this schedule.
 *             <p>
 *             This value, if set, MUST NOT exceed `transaction.maxMemoUtf8Bytes`
 *             (default 100) bytes when encoded as UTF-8.
 * @param schedulerAccountId <b>(6)</b> The scheduler account for this schedule.
 *                           <p>
 *                           This SHALL be the account that submitted the original
 *                           ScheduleCreate transaction.
 * @param payerAccountId <b>(7)</b> The explicit payer account for the scheduled transaction.
 *                       <p>
 *                       If set, this account SHALL be added to the accounts that MUST sign the
 *                       schedule before it may execute.
 * @param adminKey <b>(8)</b> The admin key for this schedule.
 *                 <p>
 *                 This key, if set, MUST sign any `schedule_delete` transaction.<br/>
 *                 If not set, then this schedule SHALL NOT be deleted, and any
 *                 `schedule_delete` transaction for this schedule SHALL fail.
 * @param scheduleValidStart <b>(9)</b> The transaction valid start value for this schedule.
 *                           <p>
 *                           This MUST be set, and SHALL be copied from the `TransactionID` of
 *                           the original `schedule_create` transaction.
 * @param providedExpirationSecond <b>(10)</b> The requested expiration time of the schedule if provided by the user.
 *                                 <p>
 *                                 If not provided in the `schedule_create` transaction, this SHALL be set
 *                                 to a default value equal to the current consensus time, forward offset by
 *                                 the maximum schedule expiration time in the current dynamic network
 *                                 configuration (typically 62 days).<br/>
 *                                 The actual `calculated_expiration_second` MAY be "earlier" than this,
 *                                 but MUST NOT be later.
 * @param calculatedExpirationSecond <b>(11)</b> The calculated expiration time of the schedule.
 *                                   <p>
 *                                   This SHALL be calculated from the requested expiration time in the
 *                                   `schedule_create` transaction, and limited by the maximum expiration time
 *                                   in the current dynamic network configuration (typically 62 days).
 *                                   <p>
 *                                   The schedule SHALL be removed from global network state after the network
 *                                   reaches a consensus time greater than or equal to this value.
 * @param resolutionTime <b>(12)</b> The consensus timestamp of the transaction that executed or deleted this schedule.
 *                       <p>
 *                       This value SHALL be set to the `current_consensus_time` when a
 *                       `schedule_delete` transaction is completed.<br/>
 *                       This value SHALL be set to the `current_consensus_time` when the
 *                       scheduled transaction is executed, either as a result of gathering the
 *                       final required signature, or, if long-term schedule execution is enabled,
 *                       at the requested execution time.
 * @param scheduledTransaction <b>(13)</b> The scheduled transaction to execute.
 *                             <p>
 *                             This MUST be one of the transaction types permitted in the current value
 *                             of the `schedule.whitelist` in the dynamic network configuration.
 * @param originalCreateTransaction <b>(14)</b> The full transaction that created this schedule.
 *                                  <p>
 *                                  This is primarily used for duplicate schedule create detection. This is
 *                                  also the source of the parent transaction ID, from which the child
 *                                  transaction ID is derived when the `scheduled_transaction` is executed.
 * @param signatoriesSupplier <b>(15)</b> All of the "primitive" keys that have already signed this schedule in a supplier.
 *                    <p>
 *                    The scheduled transaction SHALL NOT be executed before this list is
 *                    sufficient to "activate" the required keys for the scheduled transaction.<br/>
 *                    A Key SHALL NOT be stored in this list unless the corresponding private
 *                    key has signed either the original `schedule_create` transaction or a
 *                    subsequent `schedule_sign` transaction intended for, and referencing to,
 *                    this specific schedule.
 *                    <p>
 *                    The only keys stored are "primitive" keys (ED25519 or ECDSA_SECP256K1) in
 *                    order to ensure that any key list or threshold keys are correctly handled,
 *                    regardless of signing order, intervening changes, or other situations.
 *                    The `scheduled_transaction` SHALL execute only if, at the time of
 *                    execution, this list contains sufficient public keys to satisfy the
 *                    full requirements for signature on that transaction.
 */
public record Schedule(
        @Nullable ScheduleID scheduleId,
        boolean deleted,
        boolean executed,
        boolean waitForExpiry,
        @Nonnull String memo,
        @Nullable AccountID schedulerAccountId,
        @Nullable AccountID payerAccountId,
        @Nullable Key adminKey,
        @Nullable Timestamp scheduleValidStart,
        long providedExpirationSecond,
        long calculatedExpirationSecond,
        @Nullable Timestamp resolutionTime,
        @Nullable SchedulableTransactionBody scheduledTransaction,
        @Nullable TransactionBody originalCreateTransaction,
        @Nonnull Supplier<List<Key>> signatoriesSupplier) {
    /** Protobuf codec for reading and writing in protobuf format */
    public static final Codec<Schedule> PROTOBUF = new com.hedera.hapi.node.state.schedule.codec.ScheduleProtoCodec();
    /** JSON codec for reading and writing in JSON format */
    public static final JsonCodec<Schedule> JSON = new com.hedera.hapi.node.state.schedule.codec.ScheduleJsonCodec();

    /** Default instance with all fields set to default values */
    public static final Schedule DEFAULT = newBuilder().build();
    /**
     * Create a pre-populated Schedule.
     *
     * @param scheduleId <b>(1)</b> This schedule's ID within the global network state.
     *                   <p>
     *                   This value SHALL be unique within the network.,
     * @param deleted <b>(2)</b> A flag indicating this schedule is deleted.
     *                <p>
     *                A schedule SHALL either be executed or deleted, but never both.,
     * @param executed <b>(3)</b> A flag indicating this schedule has executed.
     *                 <p>
     *                 A schedule SHALL either be executed or deleted, but never both.,
     * @param waitForExpiry <b>(4)</b> A schedule flag to wait for expiration before executing.
     *                      <p>
     *                      A schedule SHALL be executed immediately when all necessary signatures
     *                      are gathered, unless this flag is set.<br/>
     *                      If this flag is set, the schedule SHALL wait until the consensus time
     *                      reaches `expiration_time_provided`, when signatures MUST again be
     *                      verified. If all required signatures are present at that time, the
     *                      schedule SHALL be executed. Otherwise the schedule SHALL expire without
     *                      execution.
     *                      <p>
     *                      Note that a schedule is always removed from state after it expires,
     *                      regardless of whether it was executed or not.,
     * @param memo <b>(5)</b> A short description for this schedule.
     *             <p>
     *             This value, if set, MUST NOT exceed `transaction.maxMemoUtf8Bytes`
     *             (default 100) bytes when encoded as UTF-8.,
     * @param schedulerAccountId <b>(6)</b> The scheduler account for this schedule.
     *                           <p>
     *                           This SHALL be the account that submitted the original
     *                           ScheduleCreate transaction.,
     * @param payerAccountId <b>(7)</b> The explicit payer account for the scheduled transaction.
     *                       <p>
     *                       If set, this account SHALL be added to the accounts that MUST sign the
     *                       schedule before it may execute.,
     * @param adminKey <b>(8)</b> The admin key for this schedule.
     *                 <p>
     *                 This key, if set, MUST sign any `schedule_delete` transaction.<br/>
     *                 If not set, then this schedule SHALL NOT be deleted, and any
     *                 `schedule_delete` transaction for this schedule SHALL fail.,
     * @param scheduleValidStart <b>(9)</b> The transaction valid start value for this schedule.
     *                           <p>
     *                           This MUST be set, and SHALL be copied from the `TransactionID` of
     *                           the original `schedule_create` transaction.,
     * @param providedExpirationSecond <b>(10)</b> The requested expiration time of the schedule if provided by the user.
     *                                 <p>
     *                                 If not provided in the `schedule_create` transaction, this SHALL be set
     *                                 to a default value equal to the current consensus time, forward offset by
     *                                 the maximum schedule expiration time in the current dynamic network
     *                                 configuration (typically 62 days).<br/>
     *                                 The actual `calculated_expiration_second` MAY be "earlier" than this,
     *                                 but MUST NOT be later.,
     * @param calculatedExpirationSecond <b>(11)</b> The calculated expiration time of the schedule.
     *                                   <p>
     *                                   This SHALL be calculated from the requested expiration time in the
     *                                   `schedule_create` transaction, and limited by the maximum expiration time
     *                                   in the current dynamic network configuration (typically 62 days).
     *                                   <p>
     *                                   The schedule SHALL be removed from global network state after the network
     *                                   reaches a consensus time greater than or equal to this value.,
     * @param resolutionTime <b>(12)</b> The consensus timestamp of the transaction that executed or deleted this schedule.
     *                       <p>
     *                       This value SHALL be set to the `current_consensus_time` when a
     *                       `schedule_delete` transaction is completed.<br/>
     *                       This value SHALL be set to the `current_consensus_time` when the
     *                       scheduled transaction is executed, either as a result of gathering the
     *                       final required signature, or, if long-term schedule execution is enabled,
     *                       at the requested execution time.,
     * @param scheduledTransaction <b>(13)</b> The scheduled transaction to execute.
     *                             <p>
     *                             This MUST be one of the transaction types permitted in the current value
     *                             of the `schedule.whitelist` in the dynamic network configuration.,
     * @param originalCreateTransaction <b>(14)</b> The full transaction that created this schedule.
     *                                  <p>
     *                                  This is primarily used for duplicate schedule create detection. This is
     *                                  also the source of the parent transaction ID, from which the child
     *                                  transaction ID is derived when the `scheduled_transaction` is executed.,
     * @param signatories <b>(15)</b> All of the "primitive" keys that have already signed this schedule.
     *                    <p>
     *                    The scheduled transaction SHALL NOT be executed before this list is
     *                    sufficient to "activate" the required keys for the scheduled transaction.<br/>
     *                    A Key SHALL NOT be stored in this list unless the corresponding private
     *                    key has signed either the original `schedule_create` transaction or a
     *                    subsequent `schedule_sign` transaction intended for, and referencing to,
     *                    this specific schedule.
     *                    <p>
     *                    The only keys stored are "primitive" keys (ED25519 or ECDSA_SECP256K1) in
     *                    order to ensure that any key list or threshold keys are correctly handled,
     *                    regardless of signing order, intervening changes, or other situations.
     *                    The `scheduled_transaction` SHALL execute only if, at the time of
     *                    execution, this list contains sufficient public keys to satisfy the
     *                    full requirements for signature on that transaction.
     */
    public Schedule(
            ScheduleID scheduleId,
            boolean deleted,
            boolean executed,
            boolean waitForExpiry,
            String memo,
            AccountID schedulerAccountId,
            AccountID payerAccountId,
            Key adminKey,
            Timestamp scheduleValidStart,
            long providedExpirationSecond,
            long calculatedExpirationSecond,
            Timestamp resolutionTime,
            SchedulableTransactionBody scheduledTransaction,
            TransactionBody originalCreateTransaction,
            List<Key> signatories) {
        this(
                scheduleId,
                deleted,
                executed,
                waitForExpiry,
                memo,
                schedulerAccountId,
                payerAccountId,
                adminKey,
                scheduleValidStart,
                providedExpirationSecond,
                calculatedExpirationSecond,
                resolutionTime,
                scheduledTransaction,
                originalCreateTransaction,
                () -> signatories == null ? Collections.emptyList() : signatories);
    }

    /**
     * Return a new builder for building a model object. This is just a shortcut for <code>new Model.Builder()</code>.
     *
     * @return a new builder
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Override the default hashCode method for
     * all other objects to make hashCode
     */
    @Override
    public int hashCode() {
        int result = 1;
        if (scheduleId != null && !scheduleId.equals(DEFAULT.scheduleId)) {
            result = 31 * result + scheduleId.hashCode();
        }
        if (deleted != DEFAULT.deleted) {
            result = 31 * result + Boolean.hashCode(deleted);
        }
        if (executed != DEFAULT.executed) {
            result = 31 * result + Boolean.hashCode(executed);
        }
        if (waitForExpiry != DEFAULT.waitForExpiry) {
            result = 31 * result + Boolean.hashCode(waitForExpiry);
        }
        if (memo != null && !memo.equals(DEFAULT.memo)) {
            result = 31 * result + memo.hashCode();
        }
        if (schedulerAccountId != null && !schedulerAccountId.equals(DEFAULT.schedulerAccountId)) {
            result = 31 * result + schedulerAccountId.hashCode();
        }
        if (payerAccountId != null && !payerAccountId.equals(DEFAULT.payerAccountId)) {
            result = 31 * result + payerAccountId.hashCode();
        }
        if (adminKey != null && !adminKey.equals(DEFAULT.adminKey)) {
            result = 31 * result + adminKey.hashCode();
        }
        if (scheduleValidStart != null && !scheduleValidStart.equals(DEFAULT.scheduleValidStart)) {
            result = 31 * result + scheduleValidStart.hashCode();
        }
        if (providedExpirationSecond != DEFAULT.providedExpirationSecond) {
            result = 31 * result + Long.hashCode(providedExpirationSecond);
        }
        if (calculatedExpirationSecond != DEFAULT.calculatedExpirationSecond) {
            result = 31 * result + Long.hashCode(calculatedExpirationSecond);
        }
        if (resolutionTime != null && !resolutionTime.equals(DEFAULT.resolutionTime)) {
            result = 31 * result + resolutionTime.hashCode();
        }
        if (scheduledTransaction != null && !scheduledTransaction.equals(DEFAULT.scheduledTransaction)) {
            result = 31 * result + scheduledTransaction.hashCode();
        }
        if (originalCreateTransaction != null && !originalCreateTransaction.equals(DEFAULT.originalCreateTransaction)) {
            result = 31 * result + originalCreateTransaction.hashCode();
        }
        final List<Key> thisSignatories = signatoriesSupplier.get();
        final List<Key> defaultSignatories = DEFAULT.signatoriesSupplier.get();
        if (!Objects.equals(thisSignatories, defaultSignatories) && thisSignatories != null) {
            for (Key key : thisSignatories) {
                result = 31 * result + (key != null ? key.hashCode() : 0);
            }
        }

        long hashCode = result;
        // Shifts: 30, 27, 16, 20, 5, 18, 10, 24, 30
        hashCode += hashCode << 30;
        hashCode ^= hashCode >>> 27;
        hashCode += hashCode << 16;
        hashCode ^= hashCode >>> 20;
        hashCode += hashCode << 5;
        hashCode ^= hashCode >>> 18;
        hashCode += hashCode << 10;
        hashCode ^= hashCode >>> 24;
        hashCode += hashCode << 30;

        return (int) hashCode;
    }

    /**
     * Override the default equals method for
     */
    @Override
    public boolean equals(Object that) {
        if (that == null || this.getClass() != that.getClass()) {
            return false;
        }
        Schedule thatObj = (Schedule) that;
        if (scheduleId == null && thatObj.scheduleId != null) {
            return false;
        }
        if (scheduleId != null && !scheduleId.equals(thatObj.scheduleId)) {
            return false;
        }
        if (deleted != thatObj.deleted) {
            return false;
        }
        if (executed != thatObj.executed) {
            return false;
        }
        if (waitForExpiry != thatObj.waitForExpiry) {
            return false;
        }
        if (memo == null && thatObj.memo != null) {
            return false;
        }
        if (memo != null && !memo.equals(thatObj.memo)) {
            return false;
        }
        if (schedulerAccountId == null && thatObj.schedulerAccountId != null) {
            return false;
        }
        if (schedulerAccountId != null && !schedulerAccountId.equals(thatObj.schedulerAccountId)) {
            return false;
        }
        if (payerAccountId == null && thatObj.payerAccountId != null) {
            return false;
        }
        if (payerAccountId != null && !payerAccountId.equals(thatObj.payerAccountId)) {
            return false;
        }
        if (adminKey == null && thatObj.adminKey != null) {
            return false;
        }
        if (adminKey != null && !adminKey.equals(thatObj.adminKey)) {
            return false;
        }
        if (scheduleValidStart == null && thatObj.scheduleValidStart != null) {
            return false;
        }
        if (scheduleValidStart != null && !scheduleValidStart.equals(thatObj.scheduleValidStart)) {
            return false;
        }
        if (providedExpirationSecond != thatObj.providedExpirationSecond) {
            return false;
        }
        if (calculatedExpirationSecond != thatObj.calculatedExpirationSecond) {
            return false;
        }
        if (resolutionTime == null && thatObj.resolutionTime != null) {
            return false;
        }
        if (resolutionTime != null && !resolutionTime.equals(thatObj.resolutionTime)) {
            return false;
        }
        if (scheduledTransaction == null && thatObj.scheduledTransaction != null) {
            return false;
        }
        if (scheduledTransaction != null && !scheduledTransaction.equals(thatObj.scheduledTransaction)) {
            return false;
        }
        if (originalCreateTransaction == null && thatObj.originalCreateTransaction != null) {
            return false;
        }
        if (originalCreateTransaction != null && !originalCreateTransaction.equals(thatObj.originalCreateTransaction)) {
            return false;
        }

        List<Key> thisSignatories = signatoriesSupplier.get();
        List<Key> thatSignatories = thatObj.signatoriesSupplier.get();
        return Objects.equals(thisSignatories, thatSignatories);
    }

    /**
     * Convenience method to check if the scheduleId has a value
     *
     * @return true of the scheduleId has a value
     */
    public boolean hasScheduleId() {
        return scheduleId != null;
    }

    /**
     * Gets the value for scheduleId if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if scheduleId is null
     * @return the value for scheduleId if it has a value, or else returns the default value
     */
    public ScheduleID scheduleIdOrElse(@Nonnull final ScheduleID defaultValue) {
        return hasScheduleId() ? scheduleId : defaultValue;
    }

    /**
     * Gets the value for scheduleId if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for scheduleId if it has a value
     * @throws NullPointerException if scheduleId is null
     */
    public @Nonnull ScheduleID scheduleIdOrThrow() {
        return requireNonNull(scheduleId, "Field scheduleId is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the scheduleId has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifScheduleId(@Nonnull final Consumer<ScheduleID> ifPresent) {
        if (hasScheduleId()) {
            ifPresent.accept(scheduleId);
        }
    }

    /**
     * Convenience method to check if the schedulerAccountId has a value
     *
     * @return true of the schedulerAccountId has a value
     */
    public boolean hasSchedulerAccountId() {
        return schedulerAccountId != null;
    }

    /**
     * Gets the value for schedulerAccountId if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if schedulerAccountId is null
     * @return the value for schedulerAccountId if it has a value, or else returns the default value
     */
    public AccountID schedulerAccountIdOrElse(@Nonnull final AccountID defaultValue) {
        return hasSchedulerAccountId() ? schedulerAccountId : defaultValue;
    }

    /**
     * Gets the value for schedulerAccountId if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for schedulerAccountId if it has a value
     * @throws NullPointerException if schedulerAccountId is null
     */
    public @Nonnull AccountID schedulerAccountIdOrThrow() {
        return requireNonNull(schedulerAccountId, "Field schedulerAccountId is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the schedulerAccountId has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifSchedulerAccountId(@Nonnull final Consumer<AccountID> ifPresent) {
        if (hasSchedulerAccountId()) {
            ifPresent.accept(schedulerAccountId);
        }
    }

    /**
     * Convenience method to check if the payerAccountId has a value
     *
     * @return true of the payerAccountId has a value
     */
    public boolean hasPayerAccountId() {
        return payerAccountId != null;
    }

    /**
     * Gets the value for payerAccountId if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if payerAccountId is null
     * @return the value for payerAccountId if it has a value, or else returns the default value
     */
    public AccountID payerAccountIdOrElse(@Nonnull final AccountID defaultValue) {
        return hasPayerAccountId() ? payerAccountId : defaultValue;
    }

    /**
     * Gets the value for payerAccountId if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for payerAccountId if it has a value
     * @throws NullPointerException if payerAccountId is null
     */
    public @Nonnull AccountID payerAccountIdOrThrow() {
        return requireNonNull(payerAccountId, "Field payerAccountId is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the payerAccountId has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifPayerAccountId(@Nonnull final Consumer<AccountID> ifPresent) {
        if (hasPayerAccountId()) {
            ifPresent.accept(payerAccountId);
        }
    }

    /**
     * Convenience method to check if the adminKey has a value
     *
     * @return true of the adminKey has a value
     */
    public boolean hasAdminKey() {
        return adminKey != null;
    }

    /**
     * Gets the value for adminKey if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if adminKey is null
     * @return the value for adminKey if it has a value, or else returns the default value
     */
    public Key adminKeyOrElse(@Nonnull final Key defaultValue) {
        return hasAdminKey() ? adminKey : defaultValue;
    }

    /**
     * Gets the value for adminKey if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for adminKey if it has a value
     * @throws NullPointerException if adminKey is null
     */
    public @Nonnull Key adminKeyOrThrow() {
        return requireNonNull(adminKey, "Field adminKey is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the adminKey has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifAdminKey(@Nonnull final Consumer<Key> ifPresent) {
        if (hasAdminKey()) {
            ifPresent.accept(adminKey);
        }
    }

    /**
     * Convenience method to check if the scheduleValidStart has a value
     *
     * @return true of the scheduleValidStart has a value
     */
    public boolean hasScheduleValidStart() {
        return scheduleValidStart != null;
    }

    /**
     * Gets the value for scheduleValidStart if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if scheduleValidStart is null
     * @return the value for scheduleValidStart if it has a value, or else returns the default value
     */
    public Timestamp scheduleValidStartOrElse(@Nonnull final Timestamp defaultValue) {
        return hasScheduleValidStart() ? scheduleValidStart : defaultValue;
    }

    /**
     * Gets the value for scheduleValidStart if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for scheduleValidStart if it has a value
     * @throws NullPointerException if scheduleValidStart is null
     */
    public @Nonnull Timestamp scheduleValidStartOrThrow() {
        return requireNonNull(scheduleValidStart, "Field scheduleValidStart is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the scheduleValidStart has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifScheduleValidStart(@Nonnull final Consumer<Timestamp> ifPresent) {
        if (hasScheduleValidStart()) {
            ifPresent.accept(scheduleValidStart);
        }
    }

    /**
     * Convenience method to check if the resolutionTime has a value
     *
     * @return true of the resolutionTime has a value
     */
    public boolean hasResolutionTime() {
        return resolutionTime != null;
    }

    /**
     * Gets the value for resolutionTime if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if resolutionTime is null
     * @return the value for resolutionTime if it has a value, or else returns the default value
     */
    public Timestamp resolutionTimeOrElse(@Nonnull final Timestamp defaultValue) {
        return hasResolutionTime() ? resolutionTime : defaultValue;
    }

    /**
     * Gets the value for resolutionTime if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for resolutionTime if it has a value
     * @throws NullPointerException if resolutionTime is null
     */
    public @Nonnull Timestamp resolutionTimeOrThrow() {
        return requireNonNull(resolutionTime, "Field resolutionTime is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the resolutionTime has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifResolutionTime(@Nonnull final Consumer<Timestamp> ifPresent) {
        if (hasResolutionTime()) {
            ifPresent.accept(resolutionTime);
        }
    }

    /**
     * Convenience method to check if the scheduledTransaction has a value
     *
     * @return true of the scheduledTransaction has a value
     */
    public boolean hasScheduledTransaction() {
        return scheduledTransaction != null;
    }

    /**
     * Gets the value for scheduledTransaction if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if scheduledTransaction is null
     * @return the value for scheduledTransaction if it has a value, or else returns the default value
     */
    public SchedulableTransactionBody scheduledTransactionOrElse(
            @Nonnull final SchedulableTransactionBody defaultValue) {
        return hasScheduledTransaction() ? scheduledTransaction : defaultValue;
    }

    /**
     * Gets the value for scheduledTransaction if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for scheduledTransaction if it has a value
     * @throws NullPointerException if scheduledTransaction is null
     */
    public @Nonnull SchedulableTransactionBody scheduledTransactionOrThrow() {
        return requireNonNull(scheduledTransaction, "Field scheduledTransaction is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the scheduledTransaction has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifScheduledTransaction(@Nonnull final Consumer<SchedulableTransactionBody> ifPresent) {
        if (hasScheduledTransaction()) {
            ifPresent.accept(scheduledTransaction);
        }
    }

    /**
     * Convenience method to check if the originalCreateTransaction has a value
     *
     * @return true of the originalCreateTransaction has a value
     */
    public boolean hasOriginalCreateTransaction() {
        return originalCreateTransaction != null;
    }

    /**
     * Gets the value for originalCreateTransaction if it has a value, or else returns the default
     * value for the type.
     *
     * @param defaultValue the default value to return if originalCreateTransaction is null
     * @return the value for originalCreateTransaction if it has a value, or else returns the default value
     */
    public TransactionBody originalCreateTransactionOrElse(@Nonnull final TransactionBody defaultValue) {
        return hasOriginalCreateTransaction() ? originalCreateTransaction : defaultValue;
    }

    /**
     * Gets the value for originalCreateTransaction if it has a value, or else throws an NPE.
     * value for the type.
     *
     * @return the value for originalCreateTransaction if it has a value
     * @throws NullPointerException if originalCreateTransaction is null
     */
    public @Nonnull TransactionBody originalCreateTransactionOrThrow() {
        return requireNonNull(originalCreateTransaction, "Field originalCreateTransaction is null");
    }

    /**
     * Executes the supplied {@link Consumer} if, and only if, the originalCreateTransaction has a value
     *
     * @param ifPresent the {@link Consumer} to execute
     */
    public void ifOriginalCreateTransaction(@Nonnull final Consumer<TransactionBody> ifPresent) {
        if (hasOriginalCreateTransaction()) {
            ifPresent.accept(originalCreateTransaction);
        }
    }

    public List<Key> signatories() {
        return signatoriesSupplier != null ? signatoriesSupplier.get() : Collections.emptyList();
    }

    /**
     * Return a builder for building a copy of this model object. It will be pre-populated with all the data from this
     * model object.
     *
     * @return a pre-populated builder
     */
    public Builder copyBuilder() {
        return new Builder(
                scheduleId,
                deleted,
                executed,
                waitForExpiry,
                memo,
                schedulerAccountId,
                payerAccountId,
                adminKey,
                scheduleValidStart,
                providedExpirationSecond,
                calculatedExpirationSecond,
                resolutionTime,
                scheduledTransaction,
                originalCreateTransaction,
                signatoriesSupplier);
    }

    /**
     * Builder class for easy creation, ideal for clean code where performance is not critical. In critical performance
     * paths use the constructor directly.
     */
    public static final class Builder {
        @Nullable
        private ScheduleID scheduleId = null;

        private boolean deleted = false;
        private boolean executed = false;
        private boolean waitForExpiry = false;

        @Nonnull
        private String memo = "";

        @Nullable
        private AccountID schedulerAccountId = null;

        @Nullable
        private AccountID payerAccountId = null;

        @Nullable
        private Key adminKey = null;

        @Nullable
        private Timestamp scheduleValidStart = null;

        private long providedExpirationSecond = 0;
        private long calculatedExpirationSecond = 0;

        @Nullable
        private Timestamp resolutionTime = null;

        @Nullable
        private SchedulableTransactionBody scheduledTransaction = null;

        @Nullable
        private TransactionBody originalCreateTransaction = null;

        @Nonnull
        private Supplier<List<Key>> signatoriesSupplier = Collections::emptyList;

        /**
         * Create an empty builder
         */
        public Builder() {}

        /**
         * Create a pre-populated Builder.
         *
         * @param scheduleId <b>(1)</b> This schedule's ID within the global network state.
         *                   <p>
         *                   This value SHALL be unique within the network.,
         * @param deleted <b>(2)</b> A flag indicating this schedule is deleted.
         *                <p>
         *                A schedule SHALL either be executed or deleted, but never both.,
         * @param executed <b>(3)</b> A flag indicating this schedule has executed.
         *                 <p>
         *                 A schedule SHALL either be executed or deleted, but never both.,
         * @param waitForExpiry <b>(4)</b> A schedule flag to wait for expiration before executing.
         *                      <p>
         *                      A schedule SHALL be executed immediately when all necessary signatures
         *                      are gathered, unless this flag is set.<br/>
         *                      If this flag is set, the schedule SHALL wait until the consensus time
         *                      reaches `expiration_time_provided`, when signatures MUST again be
         *                      verified. If all required signatures are present at that time, the
         *                      schedule SHALL be executed. Otherwise the schedule SHALL expire without
         *                      execution.
         *                      <p>
         *                      Note that a schedule is always removed from state after it expires,
         *                      regardless of whether it was executed or not.,
         * @param memo <b>(5)</b> A short description for this schedule.
         *             <p>
         *             This value, if set, MUST NOT exceed `transaction.maxMemoUtf8Bytes`
         *             (default 100) bytes when encoded as UTF-8.,
         * @param schedulerAccountId <b>(6)</b> The scheduler account for this schedule.
         *                           <p>
         *                           This SHALL be the account that submitted the original
         *                           ScheduleCreate transaction.,
         * @param payerAccountId <b>(7)</b> The explicit payer account for the scheduled transaction.
         *                       <p>
         *                       If set, this account SHALL be added to the accounts that MUST sign the
         *                       schedule before it may execute.,
         * @param adminKey <b>(8)</b> The admin key for this schedule.
         *                 <p>
         *                 This key, if set, MUST sign any `schedule_delete` transaction.<br/>
         *                 If not set, then this schedule SHALL NOT be deleted, and any
         *                 `schedule_delete` transaction for this schedule SHALL fail.,
         * @param scheduleValidStart <b>(9)</b> The transaction valid start value for this schedule.
         *                           <p>
         *                           This MUST be set, and SHALL be copied from the `TransactionID` of
         *                           the original `schedule_create` transaction.,
         * @param providedExpirationSecond <b>(10)</b> The requested expiration time of the schedule if provided by the user.
         *                                 <p>
         *                                 If not provided in the `schedule_create` transaction, this SHALL be set
         *                                 to a default value equal to the current consensus time, forward offset by
         *                                 the maximum schedule expiration time in the current dynamic network
         *                                 configuration (typically 62 days).<br/>
         *                                 The actual `calculated_expiration_second` MAY be "earlier" than this,
         *                                 but MUST NOT be later.,
         * @param calculatedExpirationSecond <b>(11)</b> The calculated expiration time of the schedule.
         *                                   <p>
         *                                   This SHALL be calculated from the requested expiration time in the
         *                                   `schedule_create` transaction, and limited by the maximum expiration time
         *                                   in the current dynamic network configuration (typically 62 days).
         *                                   <p>
         *                                   The schedule SHALL be removed from global network state after the network
         *                                   reaches a consensus time greater than or equal to this value.,
         * @param resolutionTime <b>(12)</b> The consensus timestamp of the transaction that executed or deleted this schedule.
         *                       <p>
         *                       This value SHALL be set to the `current_consensus_time` when a
         *                       `schedule_delete` transaction is completed.<br/>
         *                       This value SHALL be set to the `current_consensus_time` when the
         *                       scheduled transaction is executed, either as a result of gathering the
         *                       final required signature, or, if long-term schedule execution is enabled,
         *                       at the requested execution time.,
         * @param scheduledTransaction <b>(13)</b> The scheduled transaction to execute.
         *                             <p>
         *                             This MUST be one of the transaction types permitted in the current value
         *                             of the `schedule.whitelist` in the dynamic network configuration.,
         * @param originalCreateTransaction <b>(14)</b> The full transaction that created this schedule.
         *                                  <p>
         *                                  This is primarily used for duplicate schedule create detection. This is
         *                                  also the source of the parent transaction ID, from which the child
         *                                  transaction ID is derived when the `scheduled_transaction` is executed.,
         * @param signatoriesSupplier <b>(15)</b> All of the "primitive" keys that have already signed this schedule in a supplier.
         *                    <p>
         *                    The scheduled transaction SHALL NOT be executed before this list is
         *                    sufficient to "activate" the required keys for the scheduled transaction.<br/>
         *                    A Key SHALL NOT be stored in this list unless the corresponding private
         *                    key has signed either the original `schedule_create` transaction or a
         *                    subsequent `schedule_sign` transaction intended for, and referencing to,
         *                    this specific schedule.
         *                    <p>
         *                    The only keys stored are "primitive" keys (ED25519 or ECDSA_SECP256K1) in
         *                    order to ensure that any key list or threshold keys are correctly handled,
         *                    regardless of signing order, intervening changes, or other situations.
         *                    The `scheduled_transaction` SHALL execute only if, at the time of
         *                    execution, this list contains sufficient public keys to satisfy the
         *                    full requirements for signature on that transaction.
         */
        public Builder(
                ScheduleID scheduleId,
                boolean deleted,
                boolean executed,
                boolean waitForExpiry,
                String memo,
                AccountID schedulerAccountId,
                AccountID payerAccountId,
                Key adminKey,
                Timestamp scheduleValidStart,
                long providedExpirationSecond,
                long calculatedExpirationSecond,
                Timestamp resolutionTime,
                SchedulableTransactionBody scheduledTransaction,
                TransactionBody originalCreateTransaction,
                Supplier<List<Key>> signatoriesSupplier) {
            this.scheduleId = scheduleId;
            this.deleted = deleted;
            this.executed = executed;
            this.waitForExpiry = waitForExpiry;
            this.memo = memo != null ? memo : "";
            this.schedulerAccountId = schedulerAccountId;
            this.payerAccountId = payerAccountId;
            this.adminKey = adminKey;
            this.scheduleValidStart = scheduleValidStart;
            this.providedExpirationSecond = providedExpirationSecond;
            this.calculatedExpirationSecond = calculatedExpirationSecond;
            this.resolutionTime = resolutionTime;
            this.scheduledTransaction = scheduledTransaction;
            this.originalCreateTransaction = originalCreateTransaction;
            this.signatoriesSupplier = signatoriesSupplier;
        }

        /**
         * Build a new model record with data set on builder
         *
         * @return new model record with data set
         */
        public Schedule build() {
            return new Schedule(
                    scheduleId,
                    deleted,
                    executed,
                    waitForExpiry,
                    memo,
                    schedulerAccountId,
                    payerAccountId,
                    adminKey,
                    scheduleValidStart,
                    providedExpirationSecond,
                    calculatedExpirationSecond,
                    resolutionTime,
                    scheduledTransaction,
                    originalCreateTransaction,
                    signatoriesSupplier);
        }

        /**
         * <b>(1)</b> This schedule's ID within the global network state.
         * <p>
         * This value SHALL be unique within the network.
         *
         * @param scheduleId value to set
         * @return builder to continue building with
         */
        public Builder scheduleId(@Nullable ScheduleID scheduleId) {
            this.scheduleId = scheduleId;
            return this;
        }

        /**
         * <b>(1)</b> This schedule's ID within the global network state.
         * <p>
         * This value SHALL be unique within the network.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder scheduleId(ScheduleID.Builder builder) {
            this.scheduleId = builder.build();
            return this;
        }

        /**
         * <b>(2)</b> A flag indicating this schedule is deleted.
         * <p>
         * A schedule SHALL either be executed or deleted, but never both.
         *
         * @param deleted value to set
         * @return builder to continue building with
         */
        public Builder deleted(boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        /**
         * <b>(3)</b> A flag indicating this schedule has executed.
         * <p>
         * A schedule SHALL either be executed or deleted, but never both.
         *
         * @param executed value to set
         * @return builder to continue building with
         */
        public Builder executed(boolean executed) {
            this.executed = executed;
            return this;
        }

        /**
         * <b>(4)</b> A schedule flag to wait for expiration before executing.
         * <p>
         * A schedule SHALL be executed immediately when all necessary signatures
         * are gathered, unless this flag is set.<br/>
         * If this flag is set, the schedule SHALL wait until the consensus time
         * reaches `expiration_time_provided`, when signatures MUST again be
         * verified. If all required signatures are present at that time, the
         * schedule SHALL be executed. Otherwise the schedule SHALL expire without
         * execution.
         * <p>
         * Note that a schedule is always removed from state after it expires,
         * regardless of whether it was executed or not.
         *
         * @param waitForExpiry value to set
         * @return builder to continue building with
         */
        public Builder waitForExpiry(boolean waitForExpiry) {
            this.waitForExpiry = waitForExpiry;
            return this;
        }

        /**
         * <b>(5)</b> A short description for this schedule.
         * <p>
         * This value, if set, MUST NOT exceed `transaction.maxMemoUtf8Bytes`
         * (default 100) bytes when encoded as UTF-8.
         *
         * @param memo value to set
         * @return builder to continue building with
         */
        public Builder memo(@Nonnull String memo) {
            this.memo = memo != null ? memo : "";
            return this;
        }

        /**
         * <b>(6)</b> The scheduler account for this schedule.
         * <p>
         * This SHALL be the account that submitted the original
         * ScheduleCreate transaction.
         *
         * @param schedulerAccountId value to set
         * @return builder to continue building with
         */
        public Builder schedulerAccountId(@Nullable AccountID schedulerAccountId) {
            this.schedulerAccountId = schedulerAccountId;
            return this;
        }

        /**
         * <b>(6)</b> The scheduler account for this schedule.
         * <p>
         * This SHALL be the account that submitted the original
         * ScheduleCreate transaction.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder schedulerAccountId(AccountID.Builder builder) {
            this.schedulerAccountId = builder.build();
            return this;
        }

        /**
         * <b>(7)</b> The explicit payer account for the scheduled transaction.
         * <p>
         * If set, this account SHALL be added to the accounts that MUST sign the
         * schedule before it may execute.
         *
         * @param payerAccountId value to set
         * @return builder to continue building with
         */
        public Builder payerAccountId(@Nullable AccountID payerAccountId) {
            this.payerAccountId = payerAccountId;
            return this;
        }

        /**
         * <b>(7)</b> The explicit payer account for the scheduled transaction.
         * <p>
         * If set, this account SHALL be added to the accounts that MUST sign the
         * schedule before it may execute.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder payerAccountId(AccountID.Builder builder) {
            this.payerAccountId = builder.build();
            return this;
        }

        /**
         * <b>(8)</b> The admin key for this schedule.
         * <p>
         * This key, if set, MUST sign any `schedule_delete` transaction.<br/>
         * If not set, then this schedule SHALL NOT be deleted, and any
         * `schedule_delete` transaction for this schedule SHALL fail.
         *
         * @param adminKey value to set
         * @return builder to continue building with
         */
        public Builder adminKey(@Nullable Key adminKey) {
            this.adminKey = adminKey;
            return this;
        }

        /**
         * <b>(8)</b> The admin key for this schedule.
         * <p>
         * This key, if set, MUST sign any `schedule_delete` transaction.<br/>
         * If not set, then this schedule SHALL NOT be deleted, and any
         * `schedule_delete` transaction for this schedule SHALL fail.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder adminKey(Key.Builder builder) {
            this.adminKey = builder.build();
            return this;
        }

        /**
         * <b>(9)</b> The transaction valid start value for this schedule.
         * <p>
         * This MUST be set, and SHALL be copied from the `TransactionID` of
         * the original `schedule_create` transaction.
         *
         * @param scheduleValidStart value to set
         * @return builder to continue building with
         */
        public Builder scheduleValidStart(@Nullable Timestamp scheduleValidStart) {
            this.scheduleValidStart = scheduleValidStart;
            return this;
        }

        /**
         * <b>(9)</b> The transaction valid start value for this schedule.
         * <p>
         * This MUST be set, and SHALL be copied from the `TransactionID` of
         * the original `schedule_create` transaction.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder scheduleValidStart(Timestamp.Builder builder) {
            this.scheduleValidStart = builder.build();
            return this;
        }

        /**
         * <b>(10)</b> The requested expiration time of the schedule if provided by the user.
         * <p>
         * If not provided in the `schedule_create` transaction, this SHALL be set
         * to a default value equal to the current consensus time, forward offset by
         * the maximum schedule expiration time in the current dynamic network
         * configuration (typically 62 days).<br/>
         * The actual `calculated_expiration_second` MAY be "earlier" than this,
         * but MUST NOT be later.
         *
         * @param providedExpirationSecond value to set
         * @return builder to continue building with
         */
        public Builder providedExpirationSecond(long providedExpirationSecond) {
            this.providedExpirationSecond = providedExpirationSecond;
            return this;
        }

        /**
         * <b>(11)</b> The calculated expiration time of the schedule.
         * <p>
         * This SHALL be calculated from the requested expiration time in the
         * `schedule_create` transaction, and limited by the maximum expiration time
         * in the current dynamic network configuration (typically 62 days).
         * <p>
         * The schedule SHALL be removed from global network state after the network
         * reaches a consensus time greater than or equal to this value.
         *
         * @param calculatedExpirationSecond value to set
         * @return builder to continue building with
         */
        public Builder calculatedExpirationSecond(long calculatedExpirationSecond) {
            this.calculatedExpirationSecond = calculatedExpirationSecond;
            return this;
        }

        /**
         * <b>(12)</b> The consensus timestamp of the transaction that executed or deleted this schedule.
         * <p>
         * This value SHALL be set to the `current_consensus_time` when a
         * `schedule_delete` transaction is completed.<br/>
         * This value SHALL be set to the `current_consensus_time` when the
         * scheduled transaction is executed, either as a result of gathering the
         * final required signature, or, if long-term schedule execution is enabled,
         * at the requested execution time.
         *
         * @param resolutionTime value to set
         * @return builder to continue building with
         */
        public Builder resolutionTime(@Nullable Timestamp resolutionTime) {
            this.resolutionTime = resolutionTime;
            return this;
        }

        /**
         * <b>(12)</b> The consensus timestamp of the transaction that executed or deleted this schedule.
         * <p>
         * This value SHALL be set to the `current_consensus_time` when a
         * `schedule_delete` transaction is completed.<br/>
         * This value SHALL be set to the `current_consensus_time` when the
         * scheduled transaction is executed, either as a result of gathering the
         * final required signature, or, if long-term schedule execution is enabled,
         * at the requested execution time.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder resolutionTime(Timestamp.Builder builder) {
            this.resolutionTime = builder.build();
            return this;
        }

        /**
         * <b>(13)</b> The scheduled transaction to execute.
         * <p>
         * This MUST be one of the transaction types permitted in the current value
         * of the `schedule.whitelist` in the dynamic network configuration.
         *
         * @param scheduledTransaction value to set
         * @return builder to continue building with
         */
        public Builder scheduledTransaction(@Nullable SchedulableTransactionBody scheduledTransaction) {
            this.scheduledTransaction = scheduledTransaction;
            return this;
        }

        /**
         * <b>(13)</b> The scheduled transaction to execute.
         * <p>
         * This MUST be one of the transaction types permitted in the current value
         * of the `schedule.whitelist` in the dynamic network configuration.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder scheduledTransaction(SchedulableTransactionBody.Builder builder) {
            this.scheduledTransaction = builder.build();
            return this;
        }

        /**
         * <b>(14)</b> The full transaction that created this schedule.
         * <p>
         * This is primarily used for duplicate schedule create detection. This is
         * also the source of the parent transaction ID, from which the child
         * transaction ID is derived when the `scheduled_transaction` is executed.
         *
         * @param originalCreateTransaction value to set
         * @return builder to continue building with
         */
        public Builder originalCreateTransaction(@Nullable TransactionBody originalCreateTransaction) {
            this.originalCreateTransaction = originalCreateTransaction;
            return this;
        }

        /**
         * <b>(14)</b> The full transaction that created this schedule.
         * <p>
         * This is primarily used for duplicate schedule create detection. This is
         * also the source of the parent transaction ID, from which the child
         * transaction ID is derived when the `scheduled_transaction` is executed.
         *
         * @param builder A pre-populated builder
         * @return builder to continue building with
         */
        public Builder originalCreateTransaction(TransactionBody.Builder builder) {
            this.originalCreateTransaction = builder.build();
            return this;
        }

        /**
         * <b>(15)</b> All of the "primitive" keys that have already signed this schedule.
         * <p>
         * The scheduled transaction SHALL NOT be executed before this list is
         * sufficient to "activate" the required keys for the scheduled transaction.<br/>
         * A Key SHALL NOT be stored in this list unless the corresponding private
         * key has signed either the original `schedule_create` transaction or a
         * subsequent `schedule_sign` transaction intended for, and referencing to,
         * this specific schedule.
         * <p>
         * The only keys stored are "primitive" keys (ED25519 or ECDSA_SECP256K1) in
         * order to ensure that any key list or threshold keys are correctly handled,
         * regardless of signing order, intervening changes, or other situations.
         * The `scheduled_transaction` SHALL execute only if, at the time of
         * execution, this list contains sufficient public keys to satisfy the
         * full requirements for signature on that transaction.
         *
         * @param signatories value to set
         * @return builder to continue building with
         */
        public Builder signatories(@Nonnull List<Key> signatories) {
            this.signatoriesSupplier = () -> signatories != null ? signatories : Collections.emptyList();
            return this;
        }

        /**
         * <b>(15)</b> All of the "primitive" keys that have already signed this schedule.
         * <p>
         * The scheduled transaction SHALL NOT be executed before this list is
         * sufficient to "activate" the required keys for the scheduled transaction.<br/>
         * A Key SHALL NOT be stored in this list unless the corresponding private
         * key has signed either the original `schedule_create` transaction or a
         * subsequent `schedule_sign` transaction intended for, and referencing to,
         * this specific schedule.
         * <p>
         * The only keys stored are "primitive" keys (ED25519 or ECDSA_SECP256K1) in
         * order to ensure that any key list or threshold keys are correctly handled,
         * regardless of signing order, intervening changes, or other situations.
         * The `scheduled_transaction` SHALL execute only if, at the time of
         * execution, this list contains sufficient public keys to satisfy the
         * full requirements for signature on that transaction.
         *
         * @param signatoriesSupplier value to set
         * @return builder to continue building with
         */
        public Builder signatories(@Nonnull Supplier<List<Key>> signatoriesSupplier) {
            this.signatoriesSupplier = signatoriesSupplier;
            return this;
        }

        /**
         * <b>(15)</b> All of the "primitive" keys that have already signed this schedule.
         * <p>
         * The scheduled transaction SHALL NOT be executed before this list is
         * sufficient to "activate" the required keys for the scheduled transaction.<br/>
         * A Key SHALL NOT be stored in this list unless the corresponding private
         * key has signed either the original `schedule_create` transaction or a
         * subsequent `schedule_sign` transaction intended for, and referencing to,
         * this specific schedule.
         * <p>
         * The only keys stored are "primitive" keys (ED25519 or ECDSA_SECP256K1) in
         * order to ensure that any key list or threshold keys are correctly handled,
         * regardless of signing order, intervening changes, or other situations.
         * The `scheduled_transaction` SHALL execute only if, at the time of
         * execution, this list contains sufficient public keys to satisfy the
         * full requirements for signature on that transaction.
         *
         * @param values varargs value to be built into a list
         * @return builder to continue building with
         */
        public Builder signatories(Key... values) {
            this.signatoriesSupplier = () -> values == null ? Collections.emptyList() : List.of(values);
            return this;
        }
    }
}
