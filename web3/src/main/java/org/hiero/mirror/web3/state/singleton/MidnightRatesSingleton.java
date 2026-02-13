// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.fees.schemas.V0490FeeSchema.MIDNIGHT_RATES_STATE_ID;
import static org.hiero.mirror.web3.state.Utils.getCurrentTimestamp;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import jakarta.inject.Named;
import lombok.SneakyThrows;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
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
        final var current = systemFileLoader.load(exchangeRatesFileId, getCurrentTimestamp());
        return current != null ? ExchangeRateSet.PROTOBUF.parse(current.contents()) : cachedExchangeRateSet;
    }

    private FileID getExchangeRateFileId() {
        return FileID.newBuilder()
                .shardNum(CommonProperties.getInstance().getShard())
                .realmNum(CommonProperties.getInstance().getRealm())
                .fileNum(systemEntity.exchangeRateFile().getNum())
                .build();
    }
}
