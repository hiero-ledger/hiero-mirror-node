// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static org.hiero.mirror.common.domain.entity.EntityType.TOKEN;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.repository.AccountBalanceRepository;
import org.hiero.mirror.web3.repository.CryptoAllowanceRepository;
import org.hiero.mirror.web3.repository.NftAllowanceRepository;
import org.hiero.mirror.web3.repository.NftRepository;
import org.hiero.mirror.web3.repository.TokenAccountRepository;
import org.hiero.mirror.web3.repository.TokenAllowanceRepository;
import org.hiero.mirror.web3.state.AliasedAccountCacheManager;
import org.hiero.mirror.web3.state.CommonEntityAccessor;

/**
 * This class serves as a repository layer between hedera app services read only state and the Postgres database in mirror-node
 *
 * The object, which is read from DB is converted to the PBJ generated format, so that it can properly be utilized by the hedera app components
 * */
@Named
public class AccountReadableKVState extends AbstractAliasedAccountReadableKVState<AccountID, Account> {

    public static final String KEY = "ACCOUNTS";

    private final CommonEntityAccessor commonEntityAccessor;
    private final AliasedAccountCacheManager aliasedAccountCacheManager;

    public AccountReadableKVState(
            @Nonnull CommonEntityAccessor commonEntityAccessor,
            @Nonnull NftAllowanceRepository nftAllowanceRepository,
            @Nonnull NftRepository nftRepository,
            @Nonnull SystemEntity systemEntity,
            @Nonnull TokenAllowanceRepository tokenAllowanceRepository,
            @Nonnull CryptoAllowanceRepository cryptoAllowanceRepository,
            @Nonnull TokenAccountRepository tokenAccountRepository,
            @Nonnull AccountBalanceRepository accountBalanceRepository,
            @Nonnull MirrorNodeEvmProperties mirrorNodeEvmProperties,
            @Nonnull AliasedAccountCacheManager aliasedAccountCacheManager) {
        super(
                KEY,
                accountBalanceRepository,
                cryptoAllowanceRepository,
                nftAllowanceRepository,
                nftRepository,
                systemEntity,
                tokenAccountRepository,
                tokenAllowanceRepository,
                mirrorNodeEvmProperties);
        this.commonEntityAccessor = commonEntityAccessor;
        this.aliasedAccountCacheManager = aliasedAccountCacheManager;
    }

    @Override
    protected Account readFromDataSource(@Nonnull AccountID key) {
        final var timestamp = ContractCallContext.get().getTimestamp();
        return commonEntityAccessor
                .get(key, timestamp)
                .filter(entity -> entity.getType() != TOKEN)
                .map(entity -> {
                    final var account = accountFromEntity(entity, timestamp);
                    // Associate the account alias with this entity in the cache, if any.
                    if (account.alias().length() > 0) {
                        aliasedAccountCacheManager.putAccountAlias(account.alias(), key);
                    }
                    return account;
                })
                .orElse(null);
    }

    @Nonnull
    @Override
    public String getServiceName() {
        return "TokenService";
    }
}
