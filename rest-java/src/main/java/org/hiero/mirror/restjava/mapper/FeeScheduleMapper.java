// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.hiero.mirror.restjava.mapper.CommonMapper.QUALIFIER_TIMESTAMP;

import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import org.hiero.mirror.rest.model.NetworkFee;
import org.hiero.mirror.rest.model.NetworkFeesResponse;
import org.hiero.mirror.restjava.dto.SystemFile;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Sort;

@Mapper(config = MapperConfiguration.class, uses = CommonMapper.class)
public interface FeeScheduleMapper {

    long FEE_DIVISOR_FACTOR = 1000L;

    Comparator<NetworkFee> ASC_COMPARATOR =
            Comparator.comparing(NetworkFee::getTransactionType, String.CASE_INSENSITIVE_ORDER);
    Comparator<NetworkFee> DESC_COMPARATOR = ASC_COMPARATOR.reversed();

    Map<HederaFunctionality, String> ENABLED_TRANSACTION_TYPES = Map.of(
            HederaFunctionality.ContractCall, "ContractCall",
            HederaFunctionality.ContractCreate, "ContractCreate",
            HederaFunctionality.EthereumTransaction, "EthereumTransaction");

    @Mapping(target = "fees", expression = "java(mapFees(feeScheduleFile, exchangeRateFile, order))")
    @Mapping(
            source = "feeScheduleFile.fileData.consensusTimestamp",
            target = "timestamp",
            qualifiedByName = QUALIFIER_TIMESTAMP)
    NetworkFeesResponse map(
            SystemFile<CurrentAndNextFeeSchedule> feeScheduleFile,
            SystemFile<ExchangeRateSet> exchangeRateFile,
            Sort.Direction order);

    default java.util.List<NetworkFee> mapFees(
            SystemFile<CurrentAndNextFeeSchedule> feeScheduleFile,
            SystemFile<ExchangeRateSet> exchangeRateFile,
            Sort.Direction order) {

        var schedule = feeScheduleFile.protobuf().getCurrentFeeSchedule();
        var rate = exchangeRateFile.protobuf().getCurrentRate();

        return schedule.getTransactionFeeScheduleList().stream()
                .filter(s -> ENABLED_TRANSACTION_TYPES.containsKey(s.getHederaFunctionality()) && s.getFeesCount() > 0)
                .map(s -> mapToNetworkFee(s, rate))
                .filter(Objects::nonNull)
                .sorted(getComparator(order))
                .toList();
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
        return cents == 0 ? null : Math.max((gasPrice * hbars) / (cents * FEE_DIVISOR_FACTOR), 1L);
    }

    default Comparator<NetworkFee> getComparator(Sort.Direction order) {
        return order == Sort.Direction.DESC ? DESC_COMPARATOR : ASC_COMPARATOR;
    }
}
