// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.authorization.AuthorizerImpl;
import com.hedera.node.app.authorization.PrivilegesVerifier;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.FeeCalculatorFactory;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.fees.SimpleFeeCalculator;
import com.hedera.node.app.spi.store.ReadableStoreFactory;
import com.swirlds.config.api.Configuration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
final class FeeEstimationFeeContext implements FeeContext {

    static final Map<String, String> FEE_PROPERTIES = Map.of("fees.simpleFeesEnabled", "true");
    private static final ConfigProviderImpl CONFIG_PROVIDER = new ConfigProviderImpl(false, null, FEE_PROPERTIES);
    private static final Configuration CONFIGURATION = CONFIG_PROVIDER.getConfiguration();
    private static final Authorizer FEE_AUTHORIZER =
            new AuthorizerImpl(CONFIG_PROVIDER, new PrivilegesVerifier(CONFIG_PROVIDER));

    private final TransactionBody body;
    private final FeeTopicStore topicStore;
    private final FeeTokenStore tokenStore;

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public <T> T readableStore(@NonNull final Class<T> storeInterface) {
        if (storeInterface == ReadableTopicStore.class) {
            return (T) topicStore;
        }
        if (storeInterface == ReadableTokenStore.class) {
            return (T) tokenStore;
        }
        throw new UnsupportedOperationException("Store not supported: " + storeInterface.getName());
    }

    @Override
    @NonNull
    public ReadableStoreFactory readableStoreFactory() {
        return new ReadableStoreFactory() {
            @Override
            public <T> T readableStore(@NonNull final Class<T> storeInterface) {
                return FeeEstimationFeeContext.this.readableStore(storeInterface);
            }
        };
    }

    @Override
    @NonNull
    public Configuration configuration() {
        return CONFIGURATION;
    }

    @Override
    @NonNull
    public AccountID payer() {
        throw new UnsupportedOperationException();
    }

    @Override
    @NonNull
    public TransactionBody body() {
        return body;
    }

    @Override
    @NonNull
    public FeeCalculatorFactory feeCalculatorFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    @Nullable
    public SimpleFeeCalculator getSimpleFeeCalculator() {
        return null;
    }

    @Override
    @NonNull
    public Authorizer authorizer() {
        return FEE_AUTHORIZER;
    }

    @Override
    public int numTxnSignatures() {
        return 0;
    }

    @Override
    public int numTxnBytes() {
        return 0;
    }

    @Override
    @NonNull
    public Fees dispatchComputeFees(@NonNull final TransactionBody txBody, @NonNull final AccountID syntheticPayerId) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Nullable
    public ExchangeRate activeRate() {
        return null;
    }

    @Override
    public long getGasPriceInTinycents() {
        return 0;
    }

    @Override
    @NonNull
    public HederaFunctionality functionality() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getHighVolumeThrottleUtilization(@NonNull final HederaFunctionality functionality) {
        return 0;
    }
}
