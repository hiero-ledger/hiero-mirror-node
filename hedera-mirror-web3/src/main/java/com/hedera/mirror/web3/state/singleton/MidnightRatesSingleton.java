// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import static com.hedera.node.app.fees.schemas.V0490FeeSchema.MIDNIGHT_RATES_STATE_KEY;

import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import jakarta.inject.Named;
import lombok.SneakyThrows;

@Named
public class MidnightRatesSingleton implements SingletonState<ExchangeRateSet> {

    private final ExchangeRateSet cachedExchangeRateSet;

    @SneakyThrows
    public MidnightRatesSingleton(final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        V0490FileSchema fileSchema = new V0490FileSchema();
        this.cachedExchangeRateSet = ExchangeRateSet.PROTOBUF.parse(
                fileSchema.genesisExchangeRates(mirrorNodeEvmProperties.getVersionedConfiguration()));
    }

    @Override
    public String getKey() {
        return MIDNIGHT_RATES_STATE_KEY;
    }

    @SneakyThrows
    @Override
    public ExchangeRateSet get() {
        return cachedExchangeRateSet;
    }
}
