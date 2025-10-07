// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static org.hiero.mirror.common.domain.entity.EntityType.TOKEN;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.function.Predicate;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.utils.EvmTokenUtils;
import org.hiero.mirror.web3.repository.AccountBalanceRepository;
import org.hiero.mirror.web3.repository.CryptoAllowanceRepository;
import org.hiero.mirror.web3.repository.NftAllowanceRepository;
import org.hiero.mirror.web3.repository.NftRepository;
import org.hiero.mirror.web3.repository.TokenAccountRepository;
import org.hiero.mirror.web3.repository.TokenAllowanceRepository;
import org.hiero.mirror.web3.state.AliasedAccountCacheManager;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hyperledger.besu.datatypes.Address;

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
    private final Predicate<Address> strictSystemAccountDetector;

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
            @Nonnull AliasedAccountCacheManager aliasedAccountCacheManager,
            @Nonnull Predicate<Address> strictSystemAccountDetector) {
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
        this.strictSystemAccountDetector = strictSystemAccountDetector;
    }

    @Override
    protected Account readFromDataSource(@Nonnull AccountID key) {
        final var timestamp = ContractCallContext.get().getTimestamp();
        final var account = commonEntityAccessor
                .get(key, timestamp)
                .filter(entity -> entity.getType() != TOKEN)
                .map(entity -> {
                    final var acc = accountFromEntity(entity, timestamp);
                    // Associate the account alias with this entity in the cache, if any.
                    if (acc.alias().length() > 0) {
                        aliasedAccountCacheManager.putAccountAlias(acc.alias(), key);
                    }
                    return acc;
                })
                .orElse(null);
        // In case a system account doesn't exist in a historical contract call
        // return a dummy account to avoid errors like
        // "Non-zero net hbar change when handling body"
        if (account == null) {
            try {
                final var accountAddress =
                        EvmTokenUtils.toAddress(EntityId.of(key.shardNum(), key.realmNum(), key.accountNumOrThrow()));
                if (strictSystemAccountDetector.test(accountAddress)) {
                    return Account.newBuilder().accountId(key).tinybarBalance(0).build();
                }
            } catch (Exception ex) {
                return null;
            }
        }
        return account;
    }
}
