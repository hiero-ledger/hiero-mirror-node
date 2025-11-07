// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.util.Comparator;
import java.util.Map;
import org.hiero.mirror.rest.model.NetworkFee;
import org.hiero.mirror.rest.model.NetworkFeesResponse;
import org.hiero.mirror.restjava.dto.SystemFile;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class, uses = CommonMapper.class)
public interface FeeScheduleMapper {

    long FEE_DIVISOR_FACTOR = 1000L;

    Map<HederaFunctionality, String> ENABLED_TRANSACTION_TYPES = Map.of(
            HederaFunctionality.ContractCall, "ContractCall",
            HederaFunctionality.ContractCreate, "ContractCreate",
            HederaFunctionality.EthereumTransaction, "EthereumTransaction");

    default NetworkFeesResponse map(
            SystemFile<CurrentAndNextFeeSchedule> feeScheduleFile,
            SystemFile<ExchangeRateSet> exchangeRateFile,
            String order,
            CommonMapper commonMapper) {

        var schedule = feeScheduleFile.protobuf().getCurrentFeeSchedule();
        var rate = exchangeRateFile.protobuf().getCurrentRate();

        var fees = schedule.getTransactionFeeScheduleList().stream()
                .filter(s -> ENABLED_TRANSACTION_TYPES.containsKey(s.getHederaFunctionality()) && s.getFeesCount() > 0)
                .map(s -> mapToNetworkFee(s, rate.getHbarEquiv(), rate.getCentEquiv()))
                .filter(f -> f != null)
                .sorted(getComparator(order))
                .toList();

        return new NetworkFeesResponse()
                .fees(fees)
                .timestamp(commonMapper.mapTimestamp(feeScheduleFile.fileData().getConsensusTimestamp()));
    }

    @Nullable
    private NetworkFee mapToNetworkFee(TransactionFeeSchedule schedule, int hbarEquiv, int centEquiv) {

        var feeData = schedule.getFees(0);
        if (!feeData.hasServicedata()) {
            return null;
        }

        var type = ENABLED_TRANSACTION_TYPES.get(schedule.getHederaFunctionality());
        var gas = feeData.getServicedata().getGas();
        var tinyBars = convertGasPriceToTinyBars(gas, hbarEquiv, centEquiv);

        return tinyBars == null ? null : new NetworkFee().gas(tinyBars).transactionType(type);
    }

    @Nullable
    default Long convertGasPriceToTinyBars(long gasPrice, int hbars, int cents) {
        return cents == 0 ? null : Math.max((gasPrice * hbars) / (cents * FEE_DIVISOR_FACTOR), 1L);
    }

    default Comparator<NetworkFee> getComparator(String order) {
        var comparator = Comparator.comparing(NetworkFee::getTransactionType, String.CASE_INSENSITIVE_ORDER);
        return "desc".equalsIgnoreCase(order) ? comparator.reversed() : comparator;
    }
}
