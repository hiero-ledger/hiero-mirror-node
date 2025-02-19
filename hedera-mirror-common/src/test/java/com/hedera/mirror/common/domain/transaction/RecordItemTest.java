/*
 * Copyright (C) 2020-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.common.domain.transaction;

import static com.hedera.mirror.common.util.CommonUtils.nextBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.contract.ContractTransaction;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityTransaction;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.exception.ProtobufException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.util.Version;

@SuppressWarnings("deprecation")
class RecordItemTest {

    private static final SignatureMap SIGNATURE_MAP = SignatureMap.newBuilder()
            .addSigPair(SignaturePair.newBuilder()
                    .setEd25519(ByteString.copyFromUtf8("ed25519"))
                    .setPubKeyPrefix(ByteString.copyFromUtf8("pubKeyPrefix"))
                    .build())
            .build();

    private static final TransactionBody TRANSACTION_BODY = TransactionBody.newBuilder()
            .setTransactionFee(10L)
            .setMemo("memo")
            .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
            .build();

    private static final SignedTransaction SIGNED_TRANSACTION = SignedTransaction.newBuilder()
            .setBodyBytes(TRANSACTION_BODY.toByteString())
            .setSigMap(SIGNATURE_MAP)
            .build();
    private static final Transaction DEFAULT_TRANSACTION = Transaction.newBuilder()
            .setSignedTransactionBytes(SIGNED_TRANSACTION.toByteString())
            .build();
    private static final Version DEFAULT_HAPI_VERSION = new Version(0, 22, 0);
    private static final byte[] DEFAULT_RECORD_BYTES =
            TransactionRecord.getDefaultInstance().toByteArray();

    private static final TransactionRecord TRANSACTION_RECORD = TransactionRecord.newBuilder()
            .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
            .setMemo("memo")
            .setTransactionHash(ByteString.copyFrom(nextBytes(32)))
            .build();

    @CsvSource({
        ", FEE_SCHEDULE_FILE_PART_UPLOADED, true",
        ", SUCCESS, true",
        ", SUCCESS_BUT_MISSING_EXPECTED_OPERATION, true",
        "FEE_SCHEDULE_FILE_PART_UPLOADED, FEE_SCHEDULE_FILE_PART_UPLOADED, true",
        "SUCCESS, SUCCESS, true",
        "SUCCESS_BUT_MISSING_EXPECTED_OPERATION, SUCCESS_BUT_MISSING_EXPECTED_OPERATION, true",
        "INVALID_TRANSACTION, FEE_SCHEDULE_FILE_PART_UPLOADED, false",
        "INVALID_TRANSACTION, SUCCESS, false",
        "INVALID_TRANSACTION, SUCCESS_BUT_MISSING_EXPECTED_OPERATION, false",
        "INVALID_TRANSACTION, INVALID_TRANSACTION, false",
        ", INVALID_TRANSACTION, false"
    })
    @ParameterizedTest
    void isSuccessful(ResponseCodeEnum parentStatus, ResponseCodeEnum childStatus, boolean expected) {
        RecordItem parent = null;
        if (parentStatus != null) {
            var parentRecord = TRANSACTION_RECORD.toBuilder();
            parentRecord.getReceiptBuilder().setStatus(parentStatus);
            parent = RecordItem.builder()
                    .hapiVersion(DEFAULT_HAPI_VERSION)
                    .transactionRecord(parentRecord.build())
                    .transaction(DEFAULT_TRANSACTION)
                    .build();
        }

        var childRecord = TRANSACTION_RECORD.toBuilder();
        childRecord.getReceiptBuilder().setStatus(childStatus);
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .parent(parent)
                .transactionRecord(childRecord.build())
                .transaction(DEFAULT_TRANSACTION)
                .build();
        assertThat(recordItem.isSuccessful()).isEqualTo(expected);
    }

    @Test
    void getTransactionHashEthereum() {
        var recordItem = RecordItem.builder()
                .transaction(DEFAULT_TRANSACTION)
                .transactionRecord(TRANSACTION_RECORD)
                .build();
        assertThat(recordItem.getTransactionHash())
                .isEqualTo(TRANSACTION_RECORD.getTransactionHash().toByteArray());
    }

    @Test
    void getTransactionHashNotEthereum() {
        var recordItem = RecordItem.builder()
                .transaction(DEFAULT_TRANSACTION)
                .transactionRecord(TRANSACTION_RECORD)
                .build();
        assertThat(recordItem.getTransactionHash())
                .isEqualTo(TRANSACTION_RECORD.getTransactionHash().toByteArray());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAddEntityId(boolean accept) {
        var random = new SecureRandom();
        long id = random.nextLong(2000) + 2000L;
        var now = Instant.now();
        var payerAccountId = AccountID.newBuilder().setAccountNum(id++).build();
        long consensusTimestamp = now.getEpochSecond() * 1_000_000_000 + now.getNano();
        var validStart =
                Timestamp.newBuilder().setSeconds(now.getEpochSecond() - 1).setNanos(now.getNano());
        var transactionBody = TransactionBody.newBuilder()
                .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
                .setTransactionID(
                        TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart))
                .build();
        var signedTransaction = SignedTransaction.newBuilder()
                .setBodyBytes(transactionBody.toByteString())
                .setSigMap(SIGNATURE_MAP)
                .build();
        var transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(signedTransaction.toByteString())
                .build();
        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()))
                .setReceipt(TransactionReceipt.newBuilder().setStatus(ResponseCodeEnum.SUCCESS))
                .build();
        var recordItem = RecordItem.builder()
                .entityTransactionPredicate(e -> accept)
                .transaction(transaction)
                .transactionRecord(transactionRecord)
                .build();
        var account = EntityId.of(id);
        var expected = accept
                ? Map.of(
                        id,
                        EntityTransaction.builder()
                                .consensusTimestamp(consensusTimestamp)
                                .entityId(id)
                                .payerAccountId(EntityId.of(payerAccountId))
                                .result(ResponseCodeEnum.SUCCESS_VALUE)
                                .type(TransactionType.CRYPTOTRANSFER.getProtoId())
                                .build())
                : new HashMap<Long, EntityTransaction>();

        // when
        recordItem.addEntityId(account);

        // then
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    void testTransactionBytesWithoutTransactionBodyThrowException() {
        testException(
                Transaction.newBuilder().build().toByteArray(),
                DEFAULT_RECORD_BYTES,
                RecordItem.BAD_TRANSACTION_BODY_BYTES_MESSAGE);
    }

    @Test
    void testWithBody() {
        Transaction transaction = Transaction.newBuilder()
                .setBody(TRANSACTION_BODY)
                .setSigMap(SIGNATURE_MAP)
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(TRANSACTION_RECORD)
                .transaction(transaction)
                .build();
        assertRecordItem(transaction, recordItem);
    }

    @Test
    void testWithBodyProto() throws Exception {
        // An encoded protobuf Transaction with the body set in TransactionBody, as seen in an older proto version
        byte[] transactionFromProto = Base64.decodeBase64("CgoYCjIEbWVtb3IAGhkKFwoMcHViS2V5UHJlZml4GgdlZDI1NTE5");

        Transaction expectedTransaction = Transaction.newBuilder()
                .setBody(TRANSACTION_BODY)
                .setSigMap(SIGNATURE_MAP)
                .build();

        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(TRANSACTION_RECORD)
                .transaction(Transaction.parseFrom(transactionFromProto))
                .build();
        assertRecordItem(expectedTransaction, recordItem);
    }

    @Test
    void testWithBodyBytes() {
        Transaction transaction = Transaction.newBuilder()
                .setBodyBytes(TRANSACTION_BODY.toByteString())
                .setSigMap(SIGNATURE_MAP)
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(TRANSACTION_RECORD)
                .transaction(transaction)
                .build();
        assertRecordItem(transaction, recordItem);
    }

    @Test
    void testWithSignedTransaction() {
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SIGNED_TRANSACTION.toByteString())
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(TRANSACTION_RECORD)
                .transaction(transaction)
                .build();
        assertRecordItem(transaction, recordItem);
    }

    @Test
    void testWithParentItems() {
        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(2).setNanos(4).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(transactionRecord)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        // verify parent
        assertThat(recordItem.getParent()).isNull();
    }

    @Test
    void testWithParentTimestampNoPrevious() {
        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(2).setNanos(4).build())
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(transactionRecord)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        // verify parent
        assertThat(recordItem.getParent()).isNull();
    }

    @Test
    void testWithPreviousButNoParentTimestamp() {
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SIGNED_TRANSACTION.toByteString())
                .build();

        var parentTransactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("parent")
                .build();

        RecordItem previousRecordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(parentTransactionRecord)
                .transaction(transaction)
                .build();

        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(2).setNanos(4).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .previous(previousRecordItem)
                .transactionRecord(transactionRecord)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        // verify parent
        assertThat(recordItem.getParent()).isNull();
    }

    @Test
    void testWithNonMatchingPreviousTimestamp() {
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SIGNED_TRANSACTION.toByteString())
                .build();

        var parentTransactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("parent")
                .build();

        RecordItem previousRecordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(parentTransactionRecord)
                .transaction(transaction)
                .build();

        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(3).setNanos(4).build())
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(5).setNanos(6).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .previous(previousRecordItem)
                .transactionRecord(transactionRecord)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        // verify parent
        assertThat(recordItem.getParent()).isNull();
    }

    @Test
    void testWithParent() {
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SIGNED_TRANSACTION.toByteString())
                .build();

        var parentTransactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("parent")
                .build();

        RecordItem previousRecordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(parentTransactionRecord)
                .transaction(transaction)
                .build();

        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(3).setNanos(4).build())
                .setParentConsensusTimestamp(parentTransactionRecord.getConsensusTimestamp())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(transactionRecord)
                .previous(previousRecordItem)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        // verify parent is picked up for a valid previous
        assertThat(recordItem)
                .returns(previousRecordItem, RecordItem::getParent)
                .satisfies(c -> assertThat(c.getParent()).isNotNull());
    }

    @Test
    void testWithParentFromSiblingReference() {
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SIGNED_TRANSACTION.toByteString())
                .build();

        var parentTransactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("parent")
                .build();

        RecordItem parentRecordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(parentTransactionRecord)
                .transaction(transaction)
                .build();

        var siblingTransactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(3).setNanos(4).build())
                .setParentConsensusTimestamp(parentTransactionRecord.getConsensusTimestamp())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem siblingRecordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .parent(parentRecordItem)
                .transactionRecord(siblingTransactionRecord)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(5).setNanos(6).build())
                .setParentConsensusTimestamp(parentTransactionRecord.getConsensusTimestamp())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .previous(siblingRecordItem)
                .transactionRecord(transactionRecord)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        // verify parent is picked up for a valid previous
        assertThat(recordItem).returns(parentRecordItem, RecordItem::getParent).satisfies(c -> assertThat(c.getParent())
                .isNotNull());
    }

    @Test
    void testWithNonMatchingParentFromSiblingReference() {
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SIGNED_TRANSACTION.toByteString())
                .build();

        var parentTransactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("parent")
                .build();

        RecordItem parentRecordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(parentTransactionRecord)
                .transaction(transaction)
                .build();

        var siblingTransactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(3).setNanos(4).build())
                .setParentConsensusTimestamp(parentTransactionRecord.getConsensusTimestamp())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem siblingRecordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(siblingTransactionRecord)
                .parent(parentRecordItem)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(5).setNanos(6).build())
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(7).setNanos(8).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .previous(siblingRecordItem)
                .transactionRecord(transactionRecord)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        // verify parent
        assertThat(recordItem.getParent()).isNull();
    }

    /**
     * This test writes a TransactionBody that contains an unknown field with a protobuf ID of 9999 to test that the
     * unknown transaction is still inserted into the database.
     */
    @Test
    void unknownTransactionType() throws Exception {
        int unknownType = 9999;
        byte[] transactionBodyBytes = Hex.decodeHex(
                "0a120a0c08eb88d6ee0510e8eff7ab01120218021202180318c280de1922020878321043727970746f2074657374206d656d6ffaf004050a03666f6f");
        Transaction transaction = Transaction.newBuilder()
                .setBodyBytes(ByteString.copyFrom(transactionBodyBytes))
                .build();

        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(TRANSACTION_RECORD)
                .transaction(transaction)
                .build();

        assertThat(recordItem.getTransactionType()).isEqualTo(unknownType);
    }

    @EnumSource(
            value = ResponseCodeEnum.class,
            names = {"UNRECOGNIZED"},
            mode = EnumSource.Mode.EXCLUDE)
    @ParameterizedTest
    void isInvalidIdError(ResponseCodeEnum responseCode) {
        var receipt = TransactionReceipt.newBuilder().setStatus(responseCode).build();
        var tr = TRANSACTION_RECORD.toBuilder().setReceipt(receipt).build();
        var recordItem = RecordItem.builder()
                .transaction(DEFAULT_TRANSACTION)
                .transactionRecord(tr)
                .build();
        var types = StringUtils.join(EntityType.values(), '|');

        if (responseCode.name().matches("^INVALID_(" + types + "|ALIAS|NODE_ACCOUNT)_(ID|KEY)$")) {
            assertThat(recordItem.isInvalidIdError()).isTrue();
        } else {
            assertThat(recordItem.isInvalidIdError()).isFalse();
        }
    }

    /**
     * This test writes a TransactionBody that contains an invalid transaction body without unknown fields or a valid
     * transaction body and verifies it is still inserted into the database.
     * <p>
     * See issue #4843
     */
    @Test
    void invalidTransactionType() {
        byte[] invalidBytes = new byte[] {
            10, 23, 10, 21, 10, 11, 8, -23, -105, -78, -101, 6, 16, -115, -95, -56, 47, 18, 4, 24, -108, -74, 85, 32, 1
        };
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(ByteString.copyFrom(invalidBytes))
                .build();

        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(TRANSACTION_RECORD)
                .transaction(transaction)
                .build();

        assertThat(recordItem.getTransactionType()).isEqualTo(TransactionBody.DataCase.DATA_NOT_SET.getNumber());
    }

    @Test
    void testAddContractTransaction() {
        var random = new SecureRandom();
        long id = random.nextLong(2000) + 2000L;
        var now = Instant.now();
        var payerAccountId = AccountID.newBuilder().setAccountNum(id++).build();
        long consensusTimestamp = now.getEpochSecond() * 1_000_000_000 + now.getNano();
        var validStart =
                Timestamp.newBuilder().setSeconds(now.getEpochSecond() - 1).setNanos(now.getNano());
        var transactionBody = TransactionBody.newBuilder()
                .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
                .setTransactionID(
                        TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart))
                .build();
        var signedTransaction = SignedTransaction.newBuilder()
                .setBodyBytes(transactionBody.toByteString())
                .setSigMap(SIGNATURE_MAP)
                .build();
        var transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(signedTransaction.toByteString())
                .build();
        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()))
                .setReceipt(TransactionReceipt.newBuilder().setStatus(ResponseCodeEnum.SUCCESS))
                .build();
        var recordItem = RecordItem.builder()
                .payerAccountId(EntityId.of(payerAccountId))
                .transaction(transaction)
                .contractTransactionPredicate(entityId -> true)
                .transactionRecord(transactionRecord)
                .build();
        var account = EntityId.of(id++);

        var expected1 = ContractTransaction.builder()
                .consensusTimestamp(consensusTimestamp)
                .payerAccountId(payerAccountId.getAccountNum())
                .entityId(account.getId())
                .contractIds(Collections.singletonList(account.getId()))
                .build();

        recordItem.addContractTransaction(account);
        assertThat(recordItem.populateContractTransactions()).containsExactlyInAnyOrder(expected1);

        // adding same id doesn't result in additional record
        recordItem.addContractTransaction(account);
        assertThat(recordItem.populateContractTransactions()).containsExactlyInAnyOrder(expected1);

        // additional id results in another transaction record and the first is updated to include id of the new
        var account2 = EntityId.of(id);
        expected1.setContractIds(Arrays.asList(account.getId(), account2.getId()));
        var expected2 = ContractTransaction.builder()
                .consensusTimestamp(consensusTimestamp)
                .payerAccountId(payerAccountId.getAccountNum())
                .entityId(account2.getId())
                .contractIds(Arrays.asList(account.getId(), account2.getId()))
                .build();
        recordItem.addContractTransaction(account2);
        assertThat(recordItem.populateContractTransactions()).containsExactlyInAnyOrder(expected1, expected2);
    }

    @Test
    void testAddContractTransactionDisabled() {
        var random = new SecureRandom();
        long id = random.nextLong(2000) + 2000L;
        var now = Instant.now();
        var payerAccountId = AccountID.newBuilder().setAccountNum(id++).build();
        var validStart =
                Timestamp.newBuilder().setSeconds(now.getEpochSecond() - 1).setNanos(now.getNano());
        var transactionBody = TransactionBody.newBuilder()
                .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
                .setTransactionID(
                        TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart))
                .build();
        var signedTransaction = SignedTransaction.newBuilder()
                .setBodyBytes(transactionBody.toByteString())
                .setSigMap(SIGNATURE_MAP)
                .build();
        var transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(signedTransaction.toByteString())
                .build();
        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()))
                .setReceipt(TransactionReceipt.newBuilder().setStatus(ResponseCodeEnum.SUCCESS))
                .build();
        var recordItem = RecordItem.builder()
                .payerAccountId(EntityId.of(payerAccountId))
                .transaction(transaction)
                .contractTransactionPredicate(entityId -> false)
                .transactionRecord(transactionRecord)
                .build();

        var account = EntityId.of(id);
        recordItem.addContractTransaction(account);

        assertThat(recordItem.populateContractTransactions()).isEmpty();
    }

    @SuppressWarnings("java:S5778")
    private void testException(byte[] transactionBytes, byte[] recordBytes, String expectedMessage) {
        assertThatThrownBy(() -> RecordItem.builder()
                        .hapiVersion(DEFAULT_HAPI_VERSION)
                        .transactionRecord(TransactionRecord.parseFrom(recordBytes))
                        .transaction(Transaction.parseFrom(transactionBytes))
                        .build()
                        .getTransactionBody())
                .isInstanceOf(ProtobufException.class)
                .hasMessage(expectedMessage);
    }

    private void assertRecordItem(Transaction transaction, RecordItem recordItem) {
        assertThat(recordItem.getHapiVersion()).isEqualTo(DEFAULT_HAPI_VERSION);
        assertThat(recordItem.getTransaction()).isEqualTo(transaction);
        assertThat(recordItem.getTransactionRecord()).isEqualTo(TRANSACTION_RECORD);
        assertThat(recordItem.getTransactionBody()).isEqualTo(TRANSACTION_BODY);
        assertThat(recordItem.getSignatureMap()).isEqualTo(SIGNATURE_MAP);
    }
}
