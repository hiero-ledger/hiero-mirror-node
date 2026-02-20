// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.fees.schemas.V0490FeeSchema.MIDNIGHT_RATES_STATE_ID;
import static org.hiero.mirror.web3.state.Utils.toFileID;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import jakarta.inject.Named;
import lombok.SneakyThrows;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.state.SystemFileLoader;
import org.hiero.mirror.web3.state.Utils;

@Named
final class MidnightRatesSingleton implements SingletonState<ExchangeRateSet> {

    private static final long NANOS_PER_HOUR = 3600L * 1_000_000_000L;
    private static final long NANOS_PER_TWO_MINUTES = 2L * 60L * 1_000_000_000L;

    private final ExchangeRateSet cachedExchangeRateSet;
    private final SystemFileLoader systemFileLoader;
    private final SystemEntity systemEntity = new SystemEntity(CommonProperties.getInstance());

    @SneakyThrows
    public MidnightRatesSingleton(final EvmProperties evmProperties, final SystemFileLoader systemFileLoader) {
        V0490FileSchema fileSchema = new V0490FileSchema();
        this.cachedExchangeRateSet = ExchangeRateSet.PROTOBUF.parse(
                fileSchema.genesisExchangeRates(evmProperties.getVersionedConfiguration()));
        this.systemFileLoader = systemFileLoader;
    }

    @Override
    public int getStateId() {
        return MIDNIGHT_RATES_STATE_ID;
    }

    @Override
    public String getServiceName() {
        return FeeService.NAME;
    }

    @SneakyThrows
    @Override
    public ExchangeRateSet get() {
        final var context = ContractCallContext.get();
        long timestamp = context.getTimestamp().orElse(Utils.getCurrentTimestamp());
        // Round the timestamp to the nearest hour and two minutes - the file gets updated every hour,
        // it gets available until the first or the second minute of that hour. All timestamps up until
        // the next file update would match that file, so parse the timestamp here to reduce the number
        // of the DB calls and cache entries.
        timestamp = roundDownToHourAndTwoMinutes(timestamp);

        // Return result from the transaction cache if possible to avoid unnecessary calls to the DB
        // and protobuf parsing. The result will be correct since the record file timestamp will be
        // consistent throughout the transaction execution.
        final var cache = context.getReadCacheState(getStateId());
        final var cached = cache.get(timestamp);
        if (cached instanceof ExchangeRateSet rates) {
            return rates;
        }

        final var file = systemFileLoader.loadExchangeRates(timestamp);
        final var rates = file != null ? ExchangeRateSet.PROTOBUF.parse(file.contents()) : cachedExchangeRateSet;
        cache.put(timestamp, rates);
        return rates;
    }

    private FileID getExchangeRateFileId() {
        final var entityId = systemEntity.exchangeRateFile();
        return toFileID(entityId);
    }

    /**
     * Rounds the given consensus timestamp (nanoseconds) down to the nearest boundary at 2 minutes
     * past the hour (e.g. 00:02, 01:02, 02:02).
     */
    private static long roundDownToHourAndTwoMinutes(long consensusTimestampNanos) {
        final long hourStart = (consensusTimestampNanos / NANOS_PER_HOUR) * NANOS_PER_HOUR;
        final long boundaryThisHour = hourStart + NANOS_PER_TWO_MINUTES;
        return consensusTimestampNanos >= boundaryThisHour ? boundaryThisHour : boundaryThisHour - NANOS_PER_HOUR;
    }
}
