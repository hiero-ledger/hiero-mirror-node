// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.ALIASES_STATE_ID;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.repository.AccountBalanceRepository;
import org.hiero.mirror.web3.repository.CryptoAllowanceRepository;
import org.hiero.mirror.web3.repository.NftAllowanceRepository;
import org.hiero.mirror.web3.repository.NftRepository;
import org.hiero.mirror.web3.repository.TokenAccountRepository;
import org.hiero.mirror.web3.repository.TokenAllowanceRepository;
import org.hiero.mirror.web3.state.AliasedAccountCacheManager;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hiero.mirror.web3.state.singleton.EntityIdSingleton;
import org.jspecify.annotations.NonNull;

@Named
public class AliasesReadableKVState extends AbstractAliasedAccountReadableKVState<ProtoBytes, AccountID> {

    public static final Integer STATE_ID = ALIASES_STATE_ID;
    private final CommonEntityAccessor commonEntityAccessor;
    private final AliasedAccountCacheManager aliasedAccountCacheManager;
    private final EntityIdSingleton entityIdSingleton;

    protected AliasesReadableKVState(
            final CommonEntityAccessor commonEntityAccessor,
            @NonNull NftAllowanceRepository nftAllowanceRepository,
            @NonNull NftRepository nftRepository,
            @NonNull SystemEntity systemEntity,
            @NonNull TokenAllowanceRepository tokenAllowanceRepository,
            @NonNull CryptoAllowanceRepository cryptoAllowanceRepository,
            @NonNull TokenAccountRepository tokenAccountRepository,
            @NonNull AccountBalanceRepository accountBalanceRepository,
            @NonNull EvmProperties evmProperties,
            @NonNull AliasedAccountCacheManager aliasedAccountCacheManager,
            @NonNull EntityIdSingleton entityIdSingleton) {
        super(
                STATE_ID,
                accountBalanceRepository,
                cryptoAllowanceRepository,
                nftAllowanceRepository,
                nftRepository,
                systemEntity,
                tokenAccountRepository,
                tokenAllowanceRepository,
                evmProperties);
        this.commonEntityAccessor = commonEntityAccessor;
        this.aliasedAccountCacheManager = aliasedAccountCacheManager;
        this.entityIdSingleton = entityIdSingleton;
    }

    @Override
    protected AccountID readFromDataSource(@NonNull ProtoBytes alias) {
        final var timestamp = ContractCallContext.get().getTimestamp();
        final var entity = commonEntityAccessor.get(alias.value(), timestamp);
        return entity.map(e -> {
                    final var account = accountFromEntity(e, timestamp);
                    final var accountID = account.accountId();
                    // Put the account in the account num cache.
                    aliasedAccountCacheManager.putAccountNum(accountID, account);
                    return accountID;
                })
                .orElseGet(() -> createOverriddenAccount(alias.value()));
    }

    /**
     * An EVM address with no entity in the DB normally resolves to no account. If a state override targets it, a dummy
     * account carrying that address as its alias is created, the override (balance, nonce, code) is applied to it and
     * it is cached under a synthetic account number, so later account, bytecode and storage reads in the same call
     * resolve the overridden state. The number is taken from the reserved headroom just below the next entity number,
     * so it never collides with a DB entity or with entities created during the call.
     */
    private AccountID createOverriddenAccount(final Bytes alias) {
        final var context = ContractCallContext.get();
        final var stateOverrides = context.getStateOverrides();
        if (stateOverrides == null
                || !stateOverrides.containsKey(alias)
                || !ConversionUtils.isEvmAddress(alias)
                || ConversionUtils.isLongZeroAddress(alias.toByteArray())) {
            return null;
        }

        final var commonProperties = CommonProperties.getInstance();
        long number = entityIdSingleton.get().number();
        final var accountID = AccountID.newBuilder()
                .shardNum(commonProperties.getShard())
                .realmNum(commonProperties.getRealm())
                .accountNum(number)
                .build();

        final var account =
                Account.newBuilder().accountId(accountID).alias(alias).build();
        aliasedAccountCacheManager.putAccountNum(accountID, account);
        return accountID;
    }

    @Override
    public String getServiceName() {
        return TokenService.NAME;
    }
}
