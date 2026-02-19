// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.util.List;
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
    private static final long FEE_SCHEDULE_EXPIRATION_SECONDS = 1759972690L;
    private static final long TIMESTAMP_BEFORE_EXPIRATION_NANOS =
            (CURRENT_RATE_EXPIRATION_SECONDS - 1) * DomainUtils.NANOS_PER_SECOND;
    private static final long TIMESTAMP_AFTER_EXPIRATION_NANOS = CURRENT_RATE_EXPIRATION_SECONDS * 1_000_000_000L + 1;
    private static final long TIMESTAMP_AFTER_FEE_SCHEDULE_EXPIRATION_NANOS =
            FEE_SCHEDULE_EXPIRATION_SECONDS * DomainUtils.NANOS_PER_SECOND + 1;

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
                .returns(89L, NetworkFee::getGas);
        assertThat(fees.get(2))
                .returns("EthereumTransaction", NetworkFee::getTransactionType)
                .returns(79L, NetworkFee::getGas);
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
        final long defaultGasPrice = 852000L;
        final int defaultHbars = 30000;
        final int defaultCents = 851000;
        assertThat(mapper.convertGasPriceToTinyBars(defaultGasPrice, defaultHbars, defaultCents))
                .isEqualTo(30L);
        assertThat(mapper.convertGasPriceToTinyBars((defaultCents * 2) - 1, defaultHbars, defaultCents))
                .isEqualTo(59L);
        assertThat(mapper.convertGasPriceToTinyBars(1L, defaultHbars, defaultCents))
                .isEqualTo(1L);
        assertThat(mapper.convertGasPriceToTinyBars(defaultGasPrice, defaultHbars, 0))
                .isNull();
    }

    @Test
    void mapFiltersOutUnsupportedTransactionTypes() {
        // given
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(TIMESTAMP_BEFORE_EXPIRATION_NANOS))
                .get();
        final var feeSchedule = CurrentAndNextFeeSchedule.newBuilder()
                .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                        .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(FEE_SCHEDULE_EXPIRATION_SECONDS))
                        .addTransactionFeeSchedule(createTransactionFee(HederaFunctionality.ContractCall, 852000L))
                        .addTransactionFeeSchedule(createTransactionFee(HederaFunctionality.CryptoTransfer, 100000L))
                        .addTransactionFeeSchedule(createTransactionFee(HederaFunctionality.TokenCreate, 200000L))
                        .addTransactionFeeSchedule(
                                createTransactionFee(HederaFunctionality.EthereumTransaction, 953000L))
                        .build())
                .build();
        final var feeScheduleFile = new SystemFile<>(fileData, feeSchedule);
        final var exchangeRateFile = new SystemFile<>(fileData, EXCHANGE_RATE_SET);

        // when
        final var result = mapper.map(feeScheduleFile, exchangeRateFile, Bound.EMPTY, Sort.Direction.ASC);

        // then
        assertThat(result.getFees())
                .hasSize(2)
                .extracting(NetworkFee::getTransactionType)
                .containsExactly("ContractCall", "EthereumTransaction")
                .doesNotContain("CryptoTransfer", "TokenCreate");
    }

    @Test
    void mapFiltersOutTransactionSchedulesWithNoFees() {
        // given
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(TIMESTAMP_BEFORE_EXPIRATION_NANOS))
                .get();
        final var feeSchedule = CurrentAndNextFeeSchedule.newBuilder()
                .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                        .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(FEE_SCHEDULE_EXPIRATION_SECONDS))
                        .addTransactionFeeSchedule(createTransactionFee(HederaFunctionality.ContractCall, 852000L))
                        .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                .setHederaFunctionality(HederaFunctionality.ContractCreate)
                                .build())
                        .addTransactionFeeSchedule(
                                createTransactionFee(HederaFunctionality.EthereumTransaction, 953000L))
                        .build())
                .build();
        final var feeScheduleFile = new SystemFile<>(fileData, feeSchedule);
        final var exchangeRateFile = new SystemFile<>(fileData, EXCHANGE_RATE_SET);

        // when
        final var result = mapper.map(feeScheduleFile, exchangeRateFile, Bound.EMPTY, Sort.Direction.ASC);

        // then
        assertThat(result.getFees())
                .hasSize(2)
                .extracting(NetworkFee::getTransactionType)
                .containsExactly("ContractCall", "EthereumTransaction")
                .doesNotContain("ContractCreate");
    }

    @Test
    void mapFiltersOutTransactionSchedulesWithNoServiceData() {
        // given
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(TIMESTAMP_BEFORE_EXPIRATION_NANOS))
                .get();
        final var feeSchedule = CurrentAndNextFeeSchedule.newBuilder()
                .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                        .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(FEE_SCHEDULE_EXPIRATION_SECONDS))
                        .addTransactionFeeSchedule(createTransactionFee(HederaFunctionality.ContractCall, 852000L))
                        .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                .setHederaFunctionality(HederaFunctionality.ContractCreate)
                                .addFees(FeeData.newBuilder().build())
                                .build())
                        .addTransactionFeeSchedule(
                                createTransactionFee(HederaFunctionality.EthereumTransaction, 953000L))
                        .build())
                .build();
        final var feeScheduleFile = new SystemFile<>(fileData, feeSchedule);
        final var exchangeRateFile = new SystemFile<>(fileData, EXCHANGE_RATE_SET);

        // when
        final var result = mapper.map(feeScheduleFile, exchangeRateFile, Bound.EMPTY, Sort.Direction.ASC);

        // then
        assertThat(result.getFees())
                .hasSize(2)
                .extracting(NetworkFee::getTransactionType)
                .containsExactly("ContractCall", "EthereumTransaction")
                .doesNotContain("ContractCreate");
    }

    @Test
    void mapEmpty() {
        // given
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(null))
                .get();
        final var feeScheduleFile = new SystemFile<>(fileData, CurrentAndNextFeeSchedule.getDefaultInstance());
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

        // then: with nextRate (centEquiv=15), gas 852000 -> 852*1/15=56, 1068000 -> 1068*1/15=71, 953000 -> 953*1/15=63
        assertThat(result.getFees()).hasSize(3);
        assertThat(result.getFees().get(0))
                .returns("ContractCall", NetworkFee::getTransactionType)
                .returns(56L, NetworkFee::getGas);
        assertThat(result.getFees().get(1))
                .returns("ContractCreate", NetworkFee::getTransactionType)
                .returns(71L, NetworkFee::getGas);
        assertThat(result.getFees().get(2))
                .returns("EthereumTransaction", NetworkFee::getTransactionType)
                .returns(63L, NetworkFee::getGas);
    }

    @Test
    void mapUsesNextFeeScheduleWhenCurrentFeeScheduleExpired() {
        // given
        final var fileData = domainBuilder
                .fileData()
                .customize(f -> f.consensusTimestamp(TIMESTAMP_AFTER_FEE_SCHEDULE_EXPIRATION_NANOS))
                .get();
        final var feeSchedule = CurrentAndNextFeeSchedule.newBuilder()
                .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                        .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(FEE_SCHEDULE_EXPIRATION_SECONDS))
                        .addTransactionFeeSchedule(createTransactionFee(HederaFunctionality.ContractCall, 100000L))
                        .addTransactionFeeSchedule(createTransactionFee(HederaFunctionality.ContractCreate, 200000L))
                        .addTransactionFeeSchedule(
                                createTransactionFee(HederaFunctionality.EthereumTransaction, 300000L))
                        .build())
                .setNextFeeSchedule(FeeSchedule.newBuilder()
                        .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(1760000000L))
                        .addTransactionFeeSchedule(createTransactionFee(HederaFunctionality.ContractCall, 852000L))
                        .addTransactionFeeSchedule(createTransactionFee(HederaFunctionality.ContractCreate, 1068000L))
                        .addTransactionFeeSchedule(
                                createTransactionFee(HederaFunctionality.EthereumTransaction, 953000L))
                        .build())
                .build();
        final var feeScheduleFile = new SystemFile<>(fileData, feeSchedule);
        final var exchangeRateFile = new SystemFile<>(fileData, EXCHANGE_RATE_SET);

        // when
        final var result = mapper.map(feeScheduleFile, exchangeRateFile, Bound.EMPTY, Sort.Direction.ASC);

        // then: next fee schedule (gas 852000, 1068000, 953000) and next rate (centEquiv=15) both used
        assertThat(result.getFees()).hasSize(3);
        assertThat(result.getFees().get(0))
                .returns("ContractCall", NetworkFee::getTransactionType)
                .returns(56L, NetworkFee::getGas);
        assertThat(result.getFees().get(1))
                .returns("ContractCreate", NetworkFee::getTransactionType)
                .returns(71L, NetworkFee::getGas);
        assertThat(result.getFees().get(2))
                .returns("EthereumTransaction", NetworkFee::getTransactionType)
                .returns(63L, NetworkFee::getGas);
    }

    private CurrentAndNextFeeSchedule createFeeSchedule() {
        return CurrentAndNextFeeSchedule.newBuilder()
                .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                        .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(FEE_SCHEDULE_EXPIRATION_SECONDS))
                        .addTransactionFeeSchedule(createTransactionFee(HederaFunctionality.ContractCall, 852000L))
                        .addTransactionFeeSchedule(createTransactionFee(HederaFunctionality.ContractCreate, 1068000L))
                        .addTransactionFeeSchedule(
                                createTransactionFee(HederaFunctionality.EthereumTransaction, 953000L))
                        .build())
                .setNextFeeSchedule(FeeSchedule.newBuilder()
                        .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(1760000000L))
                        .addTransactionFeeSchedule(createTransactionFee(HederaFunctionality.ContractCall, 852000L))
                        .addTransactionFeeSchedule(createTransactionFee(HederaFunctionality.ContractCreate, 1068000L))
                        .addTransactionFeeSchedule(
                                createTransactionFee(HederaFunctionality.EthereumTransaction, 953000L))
                        .build())
                .build();
    }

    private static TransactionFeeSchedule createTransactionFee(HederaFunctionality functionality, long gas) {
        return TransactionFeeSchedule.newBuilder()
                .setHederaFunctionality(functionality)
                .addFees(FeeData.newBuilder()
                        .setServicedata(FeeComponents.newBuilder().setGas(gas).build())
                        .build())
                .build();
    }
}
