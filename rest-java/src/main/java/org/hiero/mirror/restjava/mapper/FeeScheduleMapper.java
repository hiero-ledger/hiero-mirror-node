// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.hiero.hapi.support.fees.Extra;
import org.hiero.hapi.support.fees.ExtraFeeDefinition;
import org.hiero.hapi.support.fees.FeeSchedule;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.NetworkFee;
import org.hiero.mirror.rest.model.NetworkFeesResponse;
import org.hiero.mirror.restjava.dto.SystemFile;
import org.hiero.mirror.restjava.service.Bound;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Sort;

@Mapper(config = MapperConfiguration.class)
public interface FeeScheduleMapper {

    long FEE_DIVISOR_FACTOR = 1000L;

    Comparator<NetworkFee> ASC_COMPARATOR =
            Comparator.comparing(NetworkFee::getTransactionType, String.CASE_INSENSITIVE_ORDER);
    Comparator<NetworkFee> DESC_COMPARATOR = ASC_COMPARATOR.reversed();

    Map<com.hederahashgraph.api.proto.java.HederaFunctionality, String> ENABLED_TRANSACTION_TYPES = Map.of(
            com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall, "ContractCall",
            com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate, "ContractCreate",
            com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction, "EthereumTransaction");

    default NetworkFeesResponse mapSimpleFees(
            SystemFile<FeeSchedule> feeScheduleFile,
            SystemFile<ExchangeRateSet> exchangeRateFile,
            Bound bound,
            Sort.Direction order) {
        var response = new NetworkFeesResponse();
        var consensusTimestamp = feeScheduleFile.fileData().getConsensusTimestamp();
        if (consensusTimestamp != null) {
            response.setTimestamp(DomainUtils.toTimestamp(consensusTimestamp));
        }
        response.setFees(mapSimpleFeesToNetworkFees(feeScheduleFile, exchangeRateFile, bound, order));
        return response;
    }

    default List<NetworkFee> mapFeesToNetworkFees(
            SystemFile<CurrentAndNextFeeSchedule> feeScheduleFile,
            SystemFile<ExchangeRateSet> exchangeRateFile,
            Bound bound,
            Sort.Direction order) {

        final var refTimestampNanos = getReferenceTimestampNanos(feeScheduleFile, bound);
        final var feeSchedule = getEffectiveFeeSchedule(feeScheduleFile.data(), refTimestampNanos);
        final var exchangeRate = getEffectiveExchangeRate(exchangeRateFile.data(), refTimestampNanos);

        return feeSchedule.getTransactionFeeScheduleList().stream()
                .filter(s -> ENABLED_TRANSACTION_TYPES.containsKey(s.getHederaFunctionality()) && s.getFeesCount() > 0)
                .map(s -> mapToNetworkFee(s, exchangeRate))
                .filter(Objects::nonNull)
                .sorted(getComparator(order))
                .toList();
    }

    default List<NetworkFee> mapSimpleFeesToNetworkFees(
            SystemFile<FeeSchedule> feeScheduleFile,
            SystemFile<ExchangeRateSet> exchangeRateFile,
            Bound bound,
            Sort.Direction order) {

        final var gasTinycents = getGasPriceTinycents(feeScheduleFile.data());
        if (gasTinycents == null) {
            return List.of();
        }

        final var refTimestampNanos = getReferenceTimestampNanos(feeScheduleFile, bound);
        final var exchangeRate = getEffectiveExchangeRate(exchangeRateFile.data(), refTimestampNanos);
        final var tinyBars =
                convertGasPriceToTinyBars(gasTinycents, exchangeRate.getHbarEquiv(), exchangeRate.getCentEquiv());
        if (tinyBars == null) {
            return List.of();
        }

        return ENABLED_TRANSACTION_TYPES.values().stream()
                .map(type -> new NetworkFee().gas(tinyBars).transactionType(type))
                .sorted(getComparator(order))
                .toList();
    }

    default FeeSchedule map(CurrentAndNextFeeSchedule currentAndNextFeeSchedule, long refTimestampNanos) {
        final var effectiveSchedule = getEffectiveFeeSchedule(currentAndNextFeeSchedule, refTimestampNanos);

        Long gasFeeTinycents = null;
        for (var txSchedule : effectiveSchedule.getTransactionFeeScheduleList()) {
            if (ENABLED_TRANSACTION_TYPES.containsKey(txSchedule.getHederaFunctionality())
                    && txSchedule.getFeesCount() > 0) {
                var feeData = txSchedule.getFees(0);
                if (feeData.hasServicedata()) {
                    gasFeeTinycents = feeData.getServicedata().getGas() / FEE_DIVISOR_FACTOR;
                    break;
                }
            }
        }

        if (gasFeeTinycents == null) {
            return FeeSchedule.DEFAULT;
        }

        return FeeSchedule.newBuilder()
                .extras(ExtraFeeDefinition.newBuilder()
                        .name(Extra.GAS)
                        .fee(gasFeeTinycents)
                        .build())
                .build();
    }

    private long getReferenceTimestampNanos(SystemFile<?> feeScheduleFile, Bound bound) {
        final long upperBound = bound.adjustUpperBound();

        if (upperBound == Long.MAX_VALUE) {
            final var timestamp = feeScheduleFile.fileData().getConsensusTimestamp();
            return (timestamp != null) ? timestamp : 0L;
        }

        return upperBound;
    }

    private ExchangeRate getEffectiveExchangeRate(ExchangeRateSet exchangeRateSet, long refTimestampNanos) {
        final var currentRate = exchangeRateSet.getCurrentRate();
        final var currentRateExpirationTime = currentRate.getExpirationTime().getSeconds();

        if (refTimestampNanos > currentRateExpirationTime * DomainUtils.NANOS_PER_SECOND) {
            return exchangeRateSet.getNextRate();
        }
        return currentRate;
    }

    private com.hederahashgraph.api.proto.java.FeeSchedule getEffectiveFeeSchedule(
            CurrentAndNextFeeSchedule feeSchedules, long refTimestampNanos) {
        final var currentFeeSchedule = feeSchedules.getCurrentFeeSchedule();
        final var feeScheduleExpirationTime = currentFeeSchedule.getExpiryTime().getSeconds();

        if (refTimestampNanos > feeScheduleExpirationTime * DomainUtils.NANOS_PER_SECOND) {
            return feeSchedules.getNextFeeSchedule();
        }

        return currentFeeSchedule;
    }

    @Nullable
    private NetworkFee mapToNetworkFee(TransactionFeeSchedule schedule, ExchangeRate rate) {
        var feeData = schedule.getFees(0);
        if (!feeData.hasServicedata()) {
            return null;
        }

        var type = ENABLED_TRANSACTION_TYPES.get(schedule.getHederaFunctionality());
        var gas = feeData.getServicedata().getGas();
        var tinyBars = convertLegacyGasPriceToTinyBars(gas, rate.getHbarEquiv(), rate.getCentEquiv());

        return tinyBars == null ? null : new NetworkFee().gas(tinyBars).transactionType(type);
    }

    @Nullable
    private Long getGasPriceTinycents(FeeSchedule feeSchedule) {
        for (final var extra : feeSchedule.extras()) {
            if (extra.name() == Extra.GAS) {
                return extra.fee();
            }
        }
        return null;
    }

    @Nullable
    default Long convertGasPriceToTinyBars(long gasPriceTinycents, int hbars, int cents) {
        if (cents == 0) {
            return null;
        }
        final long gasInTinyBars = gasPriceTinycents * hbars / cents;
        return Math.max(gasInTinyBars, 1L);
    }

    @Nullable
    default Long convertLegacyGasPriceToTinyBars(long gasPrice, int hbars, int cents) {
        final long gasInTinyCents = gasPrice / FEE_DIVISOR_FACTOR;
        return convertGasPriceToTinyBars(gasInTinyCents, hbars, cents);
    }

    default Comparator<NetworkFee> getComparator(Sort.Direction order) {
        return order == Sort.Direction.DESC ? DESC_COMPARATOR : ASC_COMPARATOR;
    }
}
