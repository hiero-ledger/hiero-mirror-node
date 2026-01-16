// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.NetworkFreeze;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.repository.NetworkFreezeRepository;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EntityRecordItemListenerFreezeTest extends AbstractEntityRecordItemListenerTest {

    private final NetworkFreezeRepository networkFreezeRepository;

    @Test
    void freeze() {
        var recordItem = recordItemBuilder.freeze().build();
        var freeze = recordItem.getTransactionBody().getFreeze();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(transactionRepository.count()).isOne();
        softly.assertThat(entityRepository.count()).isZero();
        softly.assertThat(cryptoTransferRepository.count()).isEqualTo(3);
        softly.assertThat(networkFreezeRepository.findAll())
                .hasSize(1)
                .first()
                .returns(recordItem.getConsensusTimestamp(), NetworkFreeze::getConsensusTimestamp)
                .returns(null, NetworkFreeze::getEndTime)
                .returns(DomainUtils.toBytes(freeze.getFileHash()), NetworkFreeze::getFileHash)
                .returns(EntityId.of(freeze.getUpdateFile()), NetworkFreeze::getFileId)
                .returns(recordItem.getPayerAccountId(), NetworkFreeze::getPayerAccountId)
                .returns(DomainUtils.timestampInNanosMax(freeze.getStartTime()), NetworkFreeze::getStartTime)
                .returns(freeze.getFreezeTypeValue(), NetworkFreeze::getType);
    }

    @Test
    void freezeInvalidTransaction() {
        var recordItem = recordItemBuilder
                .freeze()
                .status(ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE)
                .build();

        parseRecordItemAndCommit(recordItem);

        assertTransactionAndRecord(recordItem.getTransactionBody(), recordItem.getTransactionRecord());
        softly.assertThat(cryptoTransferRepository.count()).isEqualTo(3);
        softly.assertThat(entityRepository.count()).isZero();
        softly.assertThat(networkFreezeRepository.count()).isZero();
        softly.assertThat(transactionRepository.count()).isOne();
    }
}
