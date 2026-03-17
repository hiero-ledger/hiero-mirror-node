// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import com.hedera.node.app.spi.fees.FeeCalculatorFactory;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.fees.SimpleFeeCalculator;
import com.hedera.node.app.spi.store.ReadableStoreFactory;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import jakarta.inject.Named;
import java.lang.reflect.Proxy;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public final class FeeEstimationFeeContext implements FeeContext {

    static final Map<String, String> FEE_PROPERTIES = Map.of("fees.simpleFeesEnabled", "true");
    private static final Configuration CONFIGURATION =
            new ConfigProviderImpl(false, null, FEE_PROPERTIES).getConfiguration();

    // Stub authorizer: no account is privileged, so all transactions pay normal fees.
    private static final Authorizer FEE_AUTHORIZER = new Authorizer() {
        @Override
        public boolean isAuthorized(@NonNull final AccountID id, @NonNull final HederaFunctionality function) {
            return false;
        }

        @Override
        public boolean isSuperUser(@NonNull final AccountID id) {
            return false;
        }

        @Override
        public boolean isTreasury(@NonNull final AccountID id) {
            return false;
        }

        @Override
        public SystemPrivilege hasPrivilegedAuthorization(
                @NonNull final AccountID id,
                @NonNull final HederaFunctionality functionality,
                @NonNull final TransactionBody txBody) {
            return SystemPrivilege.UNNECESSARY;
        }
    };

    private final FeeTopicStore topicStore;
    private final FeeTokenStore tokenStore;

    private final ReadableStoreFactory storeFactory = new FeeReadableStoreFactory();

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
        // Stub: 0 for all count methods, no congestion scaling.
        return (T) Proxy.newProxyInstance(
                storeInterface.getClassLoader(), new Class<?>[] {storeInterface}, (proxy, method, args) -> {
                    final var ret = method.getReturnType();
                    if (ret == long.class) return 0L;
                    if (ret == int.class) return 0;
                    if (ret == boolean.class) return false;
                    return null;
                });
    }

    @Override
    @NonNull
    public ReadableStoreFactory readableStoreFactory() {
        return storeFactory;
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
        return TransactionBody.DEFAULT;
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

    private class FeeReadableStoreFactory implements ReadableStoreFactory {
        @Override
        @NonNull
        public <T> T readableStore(@NonNull final Class<T> storeInterface) {
            return FeeEstimationFeeContext.this.readableStore(storeInterface);
        }
    }
}
