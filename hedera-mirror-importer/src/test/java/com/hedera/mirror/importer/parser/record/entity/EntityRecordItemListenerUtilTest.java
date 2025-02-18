/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.transaction.Prng;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.repository.PrngRepository;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import lombok.RequiredArgsConstructor;
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
                        .extracting(com.hedera.mirror.common.domain.transaction.Transaction::getType)
                        .isEqualTo(TransactionBody.DataCase.DATA_NOT_SET.getNumber()));
    }
}
