// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountPendingAirdrop;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.authorization.AuthorizerImpl;
import com.hedera.node.app.authorization.PrivilegesVerifier;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.service.file.FileMetadata;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableAirdropStore;
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
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.restjava.service.Bound;
import org.hiero.mirror.restjava.service.FileService;

@Named
@RequiredArgsConstructor
public final class FeeEstimationFeeContext implements FeeContext {

    static final Map<String, String> FEE_PROPERTIES = Map.of("fees.simpleFeesEnabled", "true");
    private static final ConfigProviderImpl CONFIG_PROVIDER = new ConfigProviderImpl(false, null, FEE_PROPERTIES);
    private static final Configuration CONFIGURATION = CONFIG_PROVIDER.getConfiguration();
    private static final Authorizer FEE_AUTHORIZER =
            new AuthorizerImpl(CONFIG_PROVIDER, new PrivilegesVerifier(CONFIG_PROVIDER));

    private static final ReadableAccountStore EMPTY_ACCOUNT_STORE = new ReadableAccountStore() {
        @Override
        @Nullable
        public Account getAccountById(@NonNull final AccountID id) {
            return null;
        }

        @Override
        @Nullable
        public Account getAliasedAccountById(@NonNull final AccountID id) {
            return null;
        }

        @Override
        @Nullable
        public AccountID getAccountIDByAlias(final long shardNum, final long realmNum, @NonNull final Bytes alias) {
            return null;
        }

        @Override
        public boolean containsAlias(final long shardNum, final long realmNum, @NonNull final Bytes alias) {
            return false;
        }

        @Override
        public boolean contains(@NonNull final AccountID id) {
            return false;
        }

        @Override
        public long getNumberOfAccounts() {
            return 0L;
        }

        @Override
        public long sizeOfAccountState() {
            return 0L;
        }
    };

    private static final ContractStateStore EMPTY_CONTRACT_STATE_STORE = new ContractStateStore() {
        @Override
        @Nullable
        public Bytecode getBytecode(@NonNull final ContractID contractID) {
            return null;
        }

        @Override
        public void putBytecode(@NonNull final ContractID contractID, @NonNull final Bytecode code) {
            // no-op
        }

        @Override
        public void removeSlot(@NonNull final SlotKey key) {
            // no-op
        }

        @Override
        public void adjustSlotCount(final long delta) {
            // no-op
        }

        @Override
        public void putSlot(@NonNull final SlotKey key, @NonNull final SlotValue value) {
            // no-op
        }

        @Override
        @NonNull
        public Set<SlotKey> getModifiedSlotKeys() {
            return Set.of();
        }

        @Override
        @Nullable
        public SlotValue getSlotValue(@NonNull final SlotKey key) {
            return null;
        }

        @Override
        @Nullable
        public SlotValue getOriginalSlotValue(@NonNull final SlotKey key) {
            return null;
        }

        @Override
        public long getNumSlots() {
            return 0L;
        }

        @Override
        public long getNumBytecodes() {
            return 0L;
        }
    };

    private static final ReadableNftStore EMPTY_NFT_STORE = new ReadableNftStore() {
        @Override
        @Nullable
        public Nft get(@NonNull final NftID id) {
            return null;
        }

        @Override
        public long sizeOfState() {
            return 0L;
        }
    };

    private static final ReadableTokenRelationStore EMPTY_TOKEN_RELATION_STORE = new ReadableTokenRelationStore() {
        @Override
        @Nullable
        public TokenRelation get(@NonNull final AccountID accountId, @NonNull final TokenID tokenId) {
            return null;
        }

        @Override
        public long sizeOfState() {
            return 0L;
        }
    };

    private static final ReadableAirdropStore EMPTY_AIRDROP_STORE = new ReadableAirdropStore() {
        @Override
        @Nullable
        public AccountPendingAirdrop get(@NonNull final PendingAirdropId airdropId) {
            return null;
        }

        @Override
        public boolean exists(@NonNull final PendingAirdropId airdropId) {
            return false;
        }

        @Override
        public long sizeOfState() {
            return 0L;
        }
    };

    private static final ReadableFileStore EMPTY_FILE_STORE = new ReadableFileStore() {
        @Override
        @Nullable
        public FileMetadata getFileMetadata(@NonNull final FileID id) {
            return null;
        }

        @Override
        @Nullable
        public File getFileLeaf(@NonNull final FileID id) {
            return null;
        }

        @Override
        public long sizeOfState() {
            return 0L;
        }
    };

    private final FileService fileService;
    private final FeeTopicStore topicStore;
    private final FeeTokenStore tokenStore;

    private final ReadableStoreFactory storeFactory = new FeeReadableStoreFactory();
    private final ThreadLocal<TransactionBody> requestBody = new ThreadLocal<>();

    void setBody(@NonNull final TransactionBody body) {
        requestBody.set(body);
    }

    void clearBody() {
        requestBody.remove();
    }

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
        if (storeInterface == ReadableAccountStore.class) {
            return (T) EMPTY_ACCOUNT_STORE;
        }
        if (storeInterface == ContractStateStore.class) {
            return (T) EMPTY_CONTRACT_STATE_STORE;
        }
        if (storeInterface == ReadableNftStore.class) {
            return (T) EMPTY_NFT_STORE;
        }
        if (storeInterface == ReadableTokenRelationStore.class) {
            return (T) EMPTY_TOKEN_RELATION_STORE;
        }
        if (storeInterface == ReadableAirdropStore.class) {
            return (T) EMPTY_AIRDROP_STORE;
        }
        if (storeInterface == ReadableFileStore.class) {
            return (T) EMPTY_FILE_STORE;
        }
        throw new UnsupportedOperationException("Store not supported: " + storeInterface.getName());
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
        final var body = requestBody.get();
        return body != null ? body : TransactionBody.DEFAULT;
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
        try {
            final var rate = fileService.getExchangeRate(Bound.EMPTY).data().getCurrentRate();
            return ExchangeRate.newBuilder()
                    .centEquiv(rate.getCentEquiv())
                    .hbarEquiv(rate.getHbarEquiv())
                    .build();
        } catch (EntityNotFoundException e) {
            return null;
        }
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
