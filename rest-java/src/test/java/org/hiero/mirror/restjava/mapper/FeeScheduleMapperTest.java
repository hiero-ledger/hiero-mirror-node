// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import java.util.List;
import org.hiero.hapi.support.fees.Extra;
import org.hiero.hapi.support.fees.ExtraFeeDefinition;
import org.hiero.hapi.support.fees.FeeSchedule;
import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.NetworkFee;
import org.hiero.mirror.rest.model.NetworkFeesResponse;
import org.hiero.mirror.restjava.dto.SystemFile;
import org.hiero.mirror.restjava.service.Bound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

final class FeeScheduleMapperTest {

    private static final long CURRENT_RATE_EXPIRATION_SECONDS = 1759951090L;
    private static final long TIMESTAMP_BEFORE_EXPIRATION_NANOS =
            (CURRENT_RATE_EXPIRATION_SECONDS - 1) * DomainUtils.NANOS_PER_SECOND;
    private static final long TIMESTAMP_AFTER_EXPIRATION_NANOS = CURRENT_RATE_EXPIRATION_SECONDS * 1_000_000_000L + 1;

    private static final ExchangeRateSet EXCHANGE_RATE_SET = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(12)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(CURRENT_RATE_EXPIRATION_SECONDS))
                    .setHbarEquiv(1))
            .setNextRate(ExchangeRate.newBuilder()
                    .setCentEquiv(15)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(1759972690L))
                    .setHbarEquiv(1))
            .build();

    private final DomainBuilder domainBuilder = new DomainBuilder();
    private CommonMapper commonMapper;
    private FeeScheduleMapper mapper;

    @BeforeEach
    void setup() {
        commonMapper = new CommonMapperImpl();
        mapper = new FeeScheduleMapperImpl(commonMapper);
    }

    @Test
    void map() {
        // given
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(TIMESTAMP_BEFORE_EXPIRATION_NANOS))
                .get();
        final var feeSchedule = createFeeSchedule();
        final var feeScheduleFile = new SystemFile<>(fileData, feeSchedule);
        final var exchangeRateFile = new SystemFile<>(fileData, EXCHANGE_RATE_SET);

        // when
        final var result = mapper.map(feeScheduleFile, exchangeRateFile, Bound.EMPTY, Sort.Direction.ASC);

        // then
        assertThat(result)
                .returns(
                        commonMapper.mapTimestamp(fileData.getConsensusTimestamp()), NetworkFeesResponse::getTimestamp);
        assertThat(result.getFees()).hasSize(3).isSortedAccordingTo((a, b) -> a.getTransactionType()
                .compareToIgnoreCase(b.getTransactionType()));

        final var fees = result.getFees();
        assertThat(fees.getFirst())
                .returns("ContractCall", NetworkFee::getTransactionType)
                .returns(71L, NetworkFee::getGas);
        assertThat(fees.get(1))
                .returns("ContractCreate", NetworkFee::getTransactionType)
                .returns(71L, NetworkFee::getGas);
        assertThat(fees.get(2))
                .returns("EthereumTransaction", NetworkFee::getTransactionType)
                .returns(71L, NetworkFee::getGas);
    }

    @Test
    void mapWithDescOrder() {
        // given
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(TIMESTAMP_BEFORE_EXPIRATION_NANOS))
                .get();
        final var feeSchedule = createFeeSchedule();
        final var feeScheduleFile = new SystemFile<>(fileData, feeSchedule);
        final var exchangeRateFile = new SystemFile<>(fileData, EXCHANGE_RATE_SET);

        // when
        final var result = mapper.map(feeScheduleFile, exchangeRateFile, Bound.EMPTY, Sort.Direction.DESC);

        // then
        assertThat(result.getFees()).hasSize(3).isSortedAccordingTo((a, b) -> b.getTransactionType()
                .compareToIgnoreCase(a.getTransactionType()));
    }

    @Test
    void convertGasPriceToTinyBars() {
        final long defaultGasPriceTinycents = 852L;
        final int defaultHbars = 30000;
        final int defaultCents = 851000;
        assertThat(mapper.convertGasPriceToTinyBars(defaultGasPriceTinycents, defaultHbars, defaultCents))
                .isEqualTo(30L);
        assertThat(mapper.convertGasPriceToTinyBars((defaultCents * 2L) - 1, defaultHbars, defaultCents))
                .isEqualTo(59999L);
        assertThat(mapper.convertGasPriceToTinyBars(1L, defaultHbars, defaultCents))
                .isEqualTo(1L);
        assertThat(mapper.convertGasPriceToTinyBars(defaultGasPriceTinycents, defaultHbars, 0))
                .isNull();
    }

    @Test
    void mapEmptyWhenGasExtraMissing() {
        // given
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(null))
                .get();
        final var feeScheduleFile = new SystemFile<>(fileData, FeeSchedule.DEFAULT);
        final var exchangeRateFile = new SystemFile<>(fileData, ExchangeRateSet.getDefaultInstance());

        // when
        final var result = mapper.map(feeScheduleFile, exchangeRateFile, Bound.EMPTY, Sort.Direction.ASC);

        // then
        assertThat(result)
                .returns(null, NetworkFeesResponse::getTimestamp)
                .returns(List.of(), NetworkFeesResponse::getFees);
    }

    @Test
    void mapUsesNextRateWhenCurrentRateExpired() {
        // given
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(TIMESTAMP_AFTER_EXPIRATION_NANOS))
                .get();
        final var feeSchedule = createFeeSchedule();
        final var feeScheduleFile = new SystemFile<>(fileData, feeSchedule);
        final var exchangeRateFile = new SystemFile<>(fileData, EXCHANGE_RATE_SET);

        // when
        final var result = mapper.map(feeScheduleFile, exchangeRateFile, Bound.EMPTY, Sort.Direction.ASC);

        // then: with nextRate (centEquiv=15), gas 852 tinycents -> 852*1/15=56 for all types
        assertThat(result.getFees()).hasSize(3);
        assertThat(result.getFees().get(0))
                .returns("ContractCall", NetworkFee::getTransactionType)
                .returns(56L, NetworkFee::getGas);
        assertThat(result.getFees().get(1))
                .returns("ContractCreate", NetworkFee::getTransactionType)
                .returns(56L, NetworkFee::getGas);
        assertThat(result.getFees().get(2))
                .returns("EthereumTransaction", NetworkFee::getTransactionType)
                .returns(56L, NetworkFee::getGas);
    }

    private FeeSchedule createFeeSchedule() {
        return FeeSchedule.newBuilder()
                .extras(ExtraFeeDefinition.newBuilder()
                        .name(Extra.GAS)
                        .fee(852L)
                        .build())
                .build();
    }
}
