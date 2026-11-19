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
import org.hiero.mirror.restjava.parameter.TimestampParameter;
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
    private FeeScheduleMapper feeScheduleMapper;

    @BeforeEach
    void setup() {
        commonMapper = new CommonMapperImpl();
        feeScheduleMapper = new FeeScheduleMapperImpl(commonMapper);
    }

    @Test
    void mapSimpleFees() {
        // given
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(TIMESTAMP_BEFORE_EXPIRATION_NANOS))
                .get();
        final var feeSchedule = createSimpleFeeSchedule(852L);
        final var feeScheduleFile = new SystemFile<>(fileData, feeSchedule);
        final var exchangeRateFile = new SystemFile<>(fileData, EXCHANGE_RATE_SET);

        // when
        final var result = feeScheduleMapper.map(feeScheduleFile, exchangeRateFile, Bound.EMPTY, Sort.Direction.ASC);

        // then
        assertThat(result)
                .returns(
                        commonMapper.mapTimestamp(fileData.getConsensusTimestamp()), NetworkFeesResponse::getTimestamp);
        assertThat(result.getFees())
                .hasSize(3)
                .isSortedAccordingTo((a, b) -> a.getTransactionType().compareToIgnoreCase(b.getTransactionType()));

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
    void mapSimpleFeesWithDescOrder() {
        // given
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(TIMESTAMP_BEFORE_EXPIRATION_NANOS))
                .get();
        final var feeSchedule = createSimpleFeeSchedule(852L);
        final var feeScheduleFile = new SystemFile<>(fileData, feeSchedule);
        final var exchangeRateFile = new SystemFile<>(fileData, EXCHANGE_RATE_SET);

        // when
        final var result = feeScheduleMapper.map(feeScheduleFile, exchangeRateFile, Bound.EMPTY, Sort.Direction.DESC);

        // then
        assertThat(result.getFees())
                .hasSize(3)
                .isSortedAccordingTo((a, b) -> b.getTransactionType().compareToIgnoreCase(a.getTransactionType()));
    }

    @Test
    void mapSimpleFeesEmptyWhenGasExtraMissing() {
        // given
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(null))
                .get();
        final var feeScheduleFile = new SystemFile<>(fileData, FeeSchedule.DEFAULT);
        final var exchangeRateFile = new SystemFile<>(fileData, ExchangeRateSet.getDefaultInstance());

        // when
        final var result = feeScheduleMapper.map(feeScheduleFile, exchangeRateFile, Bound.EMPTY, Sort.Direction.ASC);

        // then
        assertThat(result)
                .returns(null, NetworkFeesResponse::getTimestamp)
                .returns(List.of(), NetworkFeesResponse::getFees);
    }

    @Test
    void mapSimpleFeesEmptyWhenExchangeRateCentEquivIsZero() {
        // given
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(TIMESTAMP_BEFORE_EXPIRATION_NANOS))
                .get();
        final var feeSchedule = createSimpleFeeSchedule(852L);
        final var feeScheduleFile = new SystemFile<>(fileData, feeSchedule);
        final var zeroRateSet = ExchangeRateSet.newBuilder()
                .setCurrentRate(ExchangeRate.newBuilder().setCentEquiv(0).setHbarEquiv(1))
                .build();
        final var exchangeRateFile = new SystemFile<>(fileData, zeroRateSet);

        // when
        final var result = feeScheduleMapper.map(feeScheduleFile, exchangeRateFile, Bound.EMPTY, Sort.Direction.ASC);

        // then
        assertThat(result.getFees()).isEmpty();
    }

    @Test
    void mapSimpleFeesUsesNextRateWhenCurrentRateExpired() {
        // given
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(TIMESTAMP_AFTER_EXPIRATION_NANOS))
                .get();
        final var feeSchedule = createSimpleFeeSchedule(852L);
        final var feeScheduleFile = new SystemFile<>(fileData, feeSchedule);
        final var exchangeRateFile = new SystemFile<>(fileData, EXCHANGE_RATE_SET);

        // when
        final var result = feeScheduleMapper.map(feeScheduleFile, exchangeRateFile, Bound.EMPTY, Sort.Direction.ASC);

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

    @Test
    void mapUsesBoundUpperBoundForReferenceTimestamp() {
        // given
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(TIMESTAMP_BEFORE_EXPIRATION_NANOS))
                .get();
        final var feeSchedule = createSimpleFeeSchedule(852L);
        final var feeScheduleFile = new SystemFile<>(fileData, feeSchedule);
        final var exchangeRateFile = new SystemFile<>(fileData, EXCHANGE_RATE_SET);

        // bound upper timestamp forces AFTER_EXPIRATION rate calculation
        final var timestampParam = new TimestampParameter(
                org.hiero.mirror.restjava.common.RangeOperator.EQ, TIMESTAMP_AFTER_EXPIRATION_NANOS);
        final var bound = Bound.of(new TimestampParameter[] {timestampParam}, "timestamp", null);

        // when
        final var result = feeScheduleMapper.map(feeScheduleFile, exchangeRateFile, bound, Sort.Direction.ASC);

        // then: nextRate is used due to upper bound
        assertThat(result.getFees().getFirst().getGas()).isEqualTo(56L);
    }

    @Test
    void convertGasPriceToTinyBars() {
        final long defaultGasPriceTinycents = 852L;
        final int defaultHbars = 30000;
        final int defaultCents = 851000;
        final var exchangeRate = ExchangeRate.newBuilder()
                .setHbarEquiv(defaultHbars)
                .setCentEquiv(defaultCents)
                .build();
        assertThat(feeScheduleMapper.convertGasPriceToTinyBars(defaultGasPriceTinycents, exchangeRate))
                .isEqualTo(30L);
        assertThat(feeScheduleMapper.convertGasPriceToTinyBars((defaultCents * 2L) - 1, exchangeRate))
                .isEqualTo(59999L);
        assertThat(feeScheduleMapper.convertGasPriceToTinyBars(1L, exchangeRate))
                .isEqualTo(1L);
        assertThat(feeScheduleMapper.convertGasPriceToTinyBars(
                        defaultGasPriceTinycents,
                        exchangeRate.toBuilder().setCentEquiv(0).build()))
                .isNull();
        assertThat(feeScheduleMapper.convertGasPriceToTinyBars(null, exchangeRate))
                .isNull();
        assertThat(feeScheduleMapper.convertGasPriceToTinyBars(defaultGasPriceTinycents, null))
                .isNull();
    }

    private FeeSchedule createSimpleFeeSchedule(long gasPrice) {
        return FeeSchedule.newBuilder()
                .extras(ExtraFeeDefinition.newBuilder()
                        .name(Extra.GAS)
                        .fee(gasPrice)
                        .build())
                .build();
    }
}
