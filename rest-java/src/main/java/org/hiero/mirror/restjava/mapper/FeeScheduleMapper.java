// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            SystemFile<CurrentAndNextFeeSchedule> feeScheduleFile,
            SystemFile<ExchangeRateSet> exchangeRateFile,
            Bound bound,
            Sort.Direction order);

    default List<NetworkFee> mapFees(
            SystemFile<CurrentAndNextFeeSchedule> feeScheduleFile,
            SystemFile<ExchangeRateSet> exchangeRateFile,
            Bound bound,
            Sort.Direction order) {

        final var schedule = feeScheduleFile.protobuf().getCurrentFeeSchedule();
        final var refTimestampNanos = getReferenceTimestampNanos(feeScheduleFile, bound);
        final var rate = getEffectiveExchangeRate(exchangeRateFile.protobuf(), refTimestampNanos);

        return schedule.getTransactionFeeScheduleList().stream()
                .filter(s -> ENABLED_TRANSACTION_TYPES.containsKey(s.getHederaFunctionality()) && s.getFeesCount() > 0)
                .map(s -> mapToNetworkFee(s, rate))
                .filter(Objects::nonNull)
                .sorted(getComparator(order))
                .toList();
    }

    private long getReferenceTimestampNanos(SystemFile<CurrentAndNextFeeSchedule> feeScheduleFile, Bound bound) {
        final long upperBound = (bound == null || bound.isEmpty()) ? Long.MAX_VALUE : bound.adjustUpperBound();

        if (upperBound == Long.MAX_VALUE) {
            final var timestamp = feeScheduleFile.fileData().getConsensusTimestamp();
            return (timestamp != null) ? timestamp : 0L;
        }

        return upperBound;
    }

    private ExchangeRate getEffectiveExchangeRate(ExchangeRateSet exchangeRateSet, long refTimestampNanos) {
        final var currentRate = exchangeRateSet.getCurrentRate();
        final var currentRateExpirationTime = currentRate.getExpirationTime().getSeconds();

        if (refTimestampNanos > currentRateExpirationTime * 1_000_000_000L) {
            return exchangeRateSet.getNextRate();
        }
        return currentRate;
    }

    @Nullable
    private NetworkFee mapToNetworkFee(TransactionFeeSchedule schedule, ExchangeRate rate) {

        var feeData = schedule.getFees(0);
        if (!feeData.hasServicedata()) {
            return null;
        }

        var type = ENABLED_TRANSACTION_TYPES.get(schedule.getHederaFunctionality());
        var gas = feeData.getServicedata().getGas();
        var tinyBars = convertGasPriceToTinyBars(gas, rate.getHbarEquiv(), rate.getCentEquiv());

        return tinyBars == null ? null : new NetworkFee().gas(tinyBars).transactionType(type);
    }

    @Nullable
    default Long convertGasPriceToTinyBars(long gasPrice, int hbars, int cents) {
        if (cents == 0) {
            return null;
        }
        final long gasInTinyCents = gasPrice / FEE_DIVISOR_FACTOR;
        final long gasInTinyBars = gasInTinyCents * hbars / cents;
        return Math.max(gasInTinyBars, 1L);
    }

    default Comparator<NetworkFee> getComparator(Sort.Direction order) {
        return order == Sort.Direction.DESC ? DESC_COMPARATOR : ASC_COMPARATOR;
    }
}
