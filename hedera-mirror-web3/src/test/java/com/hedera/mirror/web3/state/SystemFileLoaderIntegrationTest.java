// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state;

import static com.hedera.mirror.common.domain.transaction.TransactionType.FILECREATE;
import static com.hedera.mirror.common.domain.transaction.TransactionType.FILEUPDATE;
import static com.hedera.services.utils.EntityIdUtils.toEntityId;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.FileID;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class SystemFileLoaderIntegrationTest extends Web3IntegrationTest {

    private static final ExchangeRateSet exchangeRatesSet = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(1)
                    .setHbarEquiv(12)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(200L))
                    .build())
            .setNextRate(ExchangeRate.newBuilder()
                    .setCentEquiv(2)
                    .setHbarEquiv(31)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                    .build())
            .build();

    private static final ExchangeRateSet exchangeRatesSet2 = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(3)
                    .setHbarEquiv(14)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(300))
                    .build())
            .setNextRate(ExchangeRate.newBuilder()
                    .setCentEquiv(4)
                    .setHbarEquiv(33)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_893L))
                    .build())
            .build();

    private final SystemFileLoader systemFileLoader;

    @Test
    void loadCachingBehavior() {
        // Setup
        final var fileId = fileId(systemEntity.exchangeRateFile());
        final var entityId = toEntityId(fileId);

        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILECREATE.getProtoId())
                        .fileData(exchangeRatesSet.toByteArray())
                        .entityId(entityId)
                        .consensusTimestamp(200L))
                .persist();

        // First load - should get from DB
        final var firstLoad = systemFileLoader.load(fileId, 350L);
        assertThat(firstLoad).isNotNull();
        assertThat(firstLoad.contents()).isEqualTo(Bytes.wrap(exchangeRatesSet.toByteArray()));

        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILEUPDATE.getProtoId())
                        .fileData(exchangeRatesSet2.toByteArray())
                        .entityId(entityId)
                        .consensusTimestamp(300L))
                .persist();

        // Second load - should get from cache
        final var secondLoad = systemFileLoader.load(fileId, 350L);
        assertThat(secondLoad).isNotNull();
        assertThat(secondLoad.contents()).isEqualTo(Bytes.wrap(exchangeRatesSet.toByteArray()));
    }

    private FileID fileId(EntityId fileId) {
        return FileID.newBuilder()
                .shardNum(fileId.getShard())
                .realmNum(fileId.getRealm())
                .fileNum(fileId.getNum())
                .build();
    }
}
