// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import static com.hedera.hapi.util.HapiUtils.functionOf;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.util.UnknownHederaFunctionality;
import com.hedera.node.app.authorization.AuthorizerImpl;
import com.hedera.node.app.authorization.PrivilegesVerifier;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.service.file.FileMetadata;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.FeeCalculatorFactory;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.fees.SimpleFeeCalculator;
import com.hedera.node.app.spi.store.ReadableStoreFactory;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.CommonProperties;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
final class FeeEstimationFeeContext implements FeeContext {

    private static final ConfigProviderImpl CONFIG_PROVIDER = new ConfigProviderImpl(
            false,
            null,
            Map.of(
                    "hedera.shard",
                            String.valueOf(CommonProperties.getInstance().getShard()),
                    "hedera.realm",
                            String.valueOf(CommonProperties.getInstance().getRealm())));
    static final Configuration CONFIGURATION = CONFIG_PROVIDER.getConfiguration();
    private static final Authorizer FEE_AUTHORIZER =
            new AuthorizerImpl(CONFIG_PROVIDER, new PrivilegesVerifier(CONFIG_PROVIDER));

    // Congestion multiplier reads these in STATE mode; return 0 so multiplier stays at 1x.
    // TODO: remove once CN fixes standalone executor to use null congestionMultipliers.
    private static final ReadableAccountStore EMPTY_ACCOUNT_STORE = new ReadableAccountStore() {
        @Override
        public Account getAccountById(final AccountID id) {
            return null;
        }

        @Override
        public Account getAliasedAccountById(final AccountID id) {
            return null;
        }

        @Override
        public AccountID getAccountIDByAlias(final long shardNum, final long realmNum, final Bytes alias) {
            return null;
        }

        @Override
        public boolean containsAlias(final long shardNum, final long realmNum, final Bytes alias) {
            return false;
        }

        @Override
        public boolean contains(final AccountID id) {
            return false;
        }

        @Override
        public long getNumberOfAccounts() {
            return 0;
        }

        @Override
        public long sizeOfAccountState() {
            return 0;
        }
    };

    private static final ContractStateStore EMPTY_CONTRACT_STATE_STORE = new ContractStateStore() {
        @Override
        public Bytecode getBytecode(final ContractID contractID) {
            return null;
        }

        @Override
        public void putBytecode(final ContractID contractID, final Bytecode code) {}

        @Override
        public void removeSlot(final SlotKey key) {}

        @Override
        public void adjustSlotCount(final long delta) {}

        @Override
        public void putSlot(final SlotKey key, final SlotValue value) {}

        @Override
        public Set<SlotKey> getModifiedSlotKeys() {
            return Set.of();
        }

        @Override
        public SlotValue getSlotValue(final SlotKey key) {
            return null;
        }

        @Override
        public SlotValue getOriginalSlotValue(final SlotKey key) {
            return null;
        }

        @Override
        public long getNumSlots() {
            return 0;
        }

        @Override
        public long getNumBytecodes() {
            return 0;
        }
    };

    private static final ReadableFileStore EMPTY_FILE_STORE = new ReadableFileStore() {
        @Override
        public FileMetadata getFileMetadata(final FileID id) {
            return null;
        }

        @Override
        public File getFileLeaf(final FileID id) {
            return null;
        }

        @Override
        public long sizeOfState() {
            return 0;
        }
    };

    private static final ReadableNftStore EMPTY_NFT_STORE = new ReadableNftStore() {
        @Override
        public Nft get(final NftID id) {
            return null;
        }

        @Override
        public long sizeOfState() {
            return 0;
        }
    };

    private static final ReadableTokenRelationStore EMPTY_TOKEN_RELATION_STORE = new ReadableTokenRelationStore() {
        @Override
        public TokenRelation get(final AccountID accountId, final TokenID tokenId) {
            return null;
        }

        @Override
        public long sizeOfState() {
            return 0;
        }
    };

    private final TransactionBody body;
    private final FeeTopicStore topicStore;
    private final FeeTokenStore tokenStore;
    private final int throttleUtilization;

    @Override
    @SuppressWarnings("unchecked")
    public <T> T readableStore(@NonNull final Class<T> storeInterface) {
        if (storeInterface == ReadableTopicStore.class) {
            return (T) topicStore;
        }
        if (storeInterface == ReadableTokenStore.class) {
            return (T) tokenStore;
        }
        if (storeInterface == ReadableAccountStore.class) {
            return (T) EMPTY_ACCOUNT_STORE;
        }
        if (storeInterface == ContractStateStore.class) {
            return (T) EMPTY_CONTRACT_STATE_STORE;
        }
        if (storeInterface == ReadableFileStore.class) {
            return (T) EMPTY_FILE_STORE;
        }
        if (storeInterface == ReadableNftStore.class) {
            return (T) EMPTY_NFT_STORE;
        }
        if (storeInterface == ReadableTokenRelationStore.class) {
            return (T) EMPTY_TOKEN_RELATION_STORE;
        }
        throw new UnsupportedOperationException("Store not supported: " + storeInterface.getSimpleName());
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
        try {
            return functionOf(body);
        } catch (UnknownHederaFunctionality e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getHighVolumeThrottleUtilization(@NonNull final HederaFunctionality functionality) {
        return throttleUtilization;
    }
}
