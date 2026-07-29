// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import java.util.Comparator;
import java.util.List;
import org.hiero.hapi.support.fees.Extra;
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

    Comparator<NetworkFee> ASC_COMPARATOR =
            Comparator.comparing(NetworkFee::getTransactionType, String.CASE_INSENSITIVE_ORDER);
    Comparator<NetworkFee> DESC_COMPARATOR = ASC_COMPARATOR.reversed();

    List<String> TRANSACTION_TYPES = List.of("ContractCall", "ContractCreate", "EthereumTransaction");

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

        return TRANSACTION_TYPES.stream()
                .map(type -> new NetworkFee().gas(tinyBars).transactionType(type))
                .sorted(getComparator(order))
                .toList();
    }

    private long getReferenceTimestampNanos(SystemFile<FeeSchedule> feeScheduleFile, Bound bound) {
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

    default Comparator<NetworkFee> getComparator(Sort.Direction order) {
        return order == Sort.Direction.DESC ? DESC_COMPARATOR : ASC_COMPARATOR;
    }
}
