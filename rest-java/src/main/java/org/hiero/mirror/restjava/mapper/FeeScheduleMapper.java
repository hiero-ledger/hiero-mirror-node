// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
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
import org.mapstruct.Mapping;
import org.springframework.data.domain.Sort;

@Mapper(config = MapperConfiguration.class)
public interface FeeScheduleMapper {

    long FEE_DIVISOR_FACTOR = 1000L;

    Comparator<NetworkFee> ASC_COMPARATOR =
            Comparator.comparing(NetworkFee::getTransactionType, String.CASE_INSENSITIVE_ORDER);
    Comparator<NetworkFee> DESC_COMPARATOR = ASC_COMPARATOR.reversed();

    Map<HederaFunctionality, String> ENABLED_TRANSACTION_TYPES = Map.of(
            HederaFunctionality.ContractCall, "ContractCall",
            HederaFunctionality.ContractCreate, "ContractCreate",
            HederaFunctionality.EthereumTransaction, "EthereumTransaction");

    @Mapping(target = "fees", expression = "java(mapFees(feeScheduleFile, exchangeRateFile, bound, order))")
    @Mapping(
            source = "feeScheduleFile.fileData.consensusTimestamp",
            target = "timestamp",
            qualifiedByName = QUALIFIER_TIMESTAMP)
    NetworkFeesResponse map(
            SystemFile<FeeSchedule> feeScheduleFile,
            SystemFile<ExchangeRateSet> exchangeRateFile,
            Bound bound,
            Sort.Direction order);

    default List<NetworkFee> mapFees(
            SystemFile<FeeSchedule> feeScheduleFile,
            SystemFile<ExchangeRateSet> exchangeRateFile,
            Bound bound,
            Sort.Direction order) {

        final var gasTinycents = getGasPriceTinycents(feeScheduleFile.data());
        final var refTimestampNanos = getReferenceTimestampNanos(feeScheduleFile, bound);
        final var exchangeRate = getEffectiveExchangeRate(exchangeRateFile.data(), refTimestampNanos);
        final var tinyBars = convertGasPriceToTinyBars(gasTinycents, exchangeRate);

        return ENABLED_TRANSACTION_TYPES.values().stream()
                .map(type -> tinyBars != null ? new NetworkFee().gas(tinyBars).transactionType(type) : null)
                .filter(Objects::nonNull)
                .sorted(getComparator(order))
                .toList();
    }

    default FeeSchedule map(CurrentAndNextFeeSchedule currentAndNextFeeSchedule, long refTimestampNanos) {
        var feeSchedule = currentAndNextFeeSchedule.getCurrentFeeSchedule();

        if (refTimestampNanos > feeSchedule.getExpiryTime().getSeconds() * DomainUtils.NANOS_PER_SECOND) {
            feeSchedule = currentAndNextFeeSchedule.getNextFeeSchedule();
        }

        for (final var transactionFeeSchedule : feeSchedule.getTransactionFeeScheduleList()) {
            if (ENABLED_TRANSACTION_TYPES.containsKey(transactionFeeSchedule.getHederaFunctionality())
                    && transactionFeeSchedule.getFeesCount() > 0) {
                final var feeData = transactionFeeSchedule.getFees(0);

                if (feeData.hasServicedata()) {
                    return FeeSchedule.newBuilder()
                            .extras(ExtraFeeDefinition.newBuilder()
                                    .name(Extra.GAS)
                                    .fee(feeData.getServicedata().getGas() / FEE_DIVISOR_FACTOR)
                                    .build())
                            .build();
                }
            }
        }

        return FeeSchedule.DEFAULT;
    }

    private long getReferenceTimestampNanos(SystemFile<?> feeScheduleFile, Bound bound) {
        final long upperBound = bound.adjustUpperBound();

        if (upperBound == Long.MAX_VALUE) {
            final var timestamp = feeScheduleFile.fileData().getConsensusTimestamp();
            return timestamp != null ? timestamp : 0L;
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
    default Long convertGasPriceToTinyBars(@Nullable Long gasPriceTinycents, ExchangeRate exchangeRate) {
        if (gasPriceTinycents == null || exchangeRate == null || exchangeRate.getCentEquiv() == 0) {
            return null;
        }

        final long gasInTinyBars = gasPriceTinycents * exchangeRate.getHbarEquiv() / exchangeRate.getCentEquiv();
        return Math.max(gasInTinyBars, 1L);
    }

    private Comparator<NetworkFee> getComparator(Sort.Direction order) {
        return order == Sort.Direction.DESC ? DESC_COMPARATOR : ASC_COMPARATOR;
    }
}
