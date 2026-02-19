// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.fees.schemas.V0490FeeSchema.MIDNIGHT_RATES_STATE_ID;

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

@Named
final class MidnightRatesSingleton implements SingletonState<ExchangeRateSet> {

    private final ExchangeRateSet cachedExchangeRateSet;
    private final SystemFileLoader systemFileLoader;
    private final FileID exchangeRatesFileId;
    private SystemEntity systemEntity = new SystemEntity(CommonProperties.getInstance());

    @SneakyThrows
    public MidnightRatesSingleton(final EvmProperties evmProperties, final SystemFileLoader systemFileLoader) {
        V0490FileSchema fileSchema = new V0490FileSchema();
        this.cachedExchangeRateSet = ExchangeRateSet.PROTOBUF.parse(
                fileSchema.genesisExchangeRates(evmProperties.getVersionedConfiguration()));
        this.systemFileLoader = systemFileLoader;
        this.exchangeRatesFileId = getExchangeRateFileId();
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
        final long timestamp = context.getTimestamp().orElse(Long.MAX_VALUE);

        // Return result from the transaction cache if possible to avoid unnecessary calls to the DB
        // and protobuf parsing. The result will be correct since the record file timestamp will be
        // consistent throughout the transaction execution.
        final var cache = context.getReadCacheState(getStateId());
        final var cached = cache.get(timestamp);
        if (cached instanceof ExchangeRateSet rates) {
            return rates;
        }

        final var file = systemFileLoader.load(exchangeRatesFileId, timestamp);
        final var rates = file != null ? ExchangeRateSet.PROTOBUF.parse(file.contents()) : cachedExchangeRateSet;
        cache.put(timestamp, rates);
        return rates;
    }

    private FileID getExchangeRateFileId() {
        final var fileId = systemEntity.exchangeRateFile();
        return FileID.newBuilder()
                .shardNum(fileId.getShard())
                .realmNum(fileId.getRealm())
                .fileNum(fileId.getNum())
                .build();
    }
}
