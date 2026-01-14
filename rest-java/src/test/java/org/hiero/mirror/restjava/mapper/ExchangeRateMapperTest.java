// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.rest.model.NetworkExchangeRateSetResponse;
import org.hiero.mirror.restjava.dto.SystemFile;
import org.junit.jupiter.api.Test;

final class ExchangeRateMapperTest {

    private static final ExchangeRateSet EXCHANGE_RATE_SET = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(12)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(1759951090L))
                    .setHbarEquiv(1))
            .setNextRate(ExchangeRate.newBuilder()
                    .setCentEquiv(13)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(1759972690L))
                    .setHbarEquiv(1))
            .build();

    private final CommonMapper commonMapper = new CommonMapperImpl();
    private final DomainBuilder domainBuilder = new DomainBuilder();
    private final ExchangeRateMapper mapper = new ExchangeRateMapperImpl(commonMapper);

    @Test
    void map() {
        // given
        final var fileData = domainBuilder.fileData().get();
        final var systemFile = new SystemFile<>(fileData, EXCHANGE_RATE_SET);
        final var currentRate = EXCHANGE_RATE_SET.getCurrentRate();
        final var nextRate = EXCHANGE_RATE_SET.getNextRate();

        // when
        final var result = mapper.map(systemFile);

        // then
        assertThat(result)
                .returns(currentRate.getCentEquiv(), n -> n.getCurrentRate().getCentEquivalent())
                .returns(currentRate.getExpirationTime().getSeconds(), n -> n.getCurrentRate()
                        .getExpirationTime())
                .returns(currentRate.getHbarEquiv(), n -> n.getCurrentRate().getHbarEquivalent())
                .returns(nextRate.getCentEquiv(), n -> n.getNextRate().getCentEquivalent())
                .returns(nextRate.getExpirationTime().getSeconds(), n -> n.getNextRate()
                        .getExpirationTime())
                .returns(nextRate.getHbarEquiv(), n -> n.getNextRate().getHbarEquivalent())
                .returns(
                        commonMapper.mapTimestamp(fileData.getConsensusTimestamp()),
                        NetworkExchangeRateSetResponse::getTimestamp);
    }

    @Test
    void mapNulls() {
        // given
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(null))
                .get();
        final var systemFile = new SystemFile<>(fileData, ExchangeRateSet.getDefaultInstance());

        // when
        final var result = mapper.map(systemFile);

        // then
        assertThat(result)
                .returns(null, NetworkExchangeRateSetResponse::getCurrentRate)
                .returns(null, NetworkExchangeRateSetResponse::getNextRate)
                .returns(null, NetworkExchangeRateSetResponse::getTimestamp);
    }
}
