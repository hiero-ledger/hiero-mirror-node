// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.ACCOUNTS_STATE_ID;
import static org.hiero.mirror.common.domain.entity.EntityType.SCHEDULE;
import static org.hiero.mirror.common.domain.entity.EntityType.TOKEN;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import jakarta.inject.Named;
import java.util.Optional;
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
import org.hiero.mirror.web3.utils.AccountDetector;
import org.jspecify.annotations.NonNull;

/**
 * This class serves as a repository layer between hedera app services read only state and the Postgres database in mirror-node
 *
 * The object, which is read from DB is converted to the PBJ generated format, so that it can properly be utilized by the hedera app components
 * */
@Named
public class AccountReadableKVState extends AbstractAliasedAccountReadableKVState<AccountID, Account> {

    public static final String KEY = "ACCOUNTS";
    public static final int STATE_ID = ACCOUNTS_STATE_ID;

    private final CommonEntityAccessor commonEntityAccessor;
    private final AliasedAccountCacheManager aliasedAccountCacheManager;

    public AccountReadableKVState(
            @NonNull CommonEntityAccessor commonEntityAccessor,
            @NonNull NftAllowanceRepository nftAllowanceRepository,
            @NonNull NftRepository nftRepository,
            @NonNull SystemEntity systemEntity,
            @NonNull TokenAllowanceRepository tokenAllowanceRepository,
            @NonNull CryptoAllowanceRepository cryptoAllowanceRepository,
            @NonNull TokenAccountRepository tokenAccountRepository,
            @NonNull AccountBalanceRepository accountBalanceRepository,
            @NonNull MirrorNodeEvmProperties mirrorNodeEvmProperties,
            @NonNull AliasedAccountCacheManager aliasedAccountCacheManager) {
        super(
                STATE_ID,
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
    protected Account readFromDataSource(@NonNull AccountID key) {
        final var timestamp = ContractCallContext.get().getTimestamp();
        return commonEntityAccessor
                .get(key, timestamp)
                .filter(entity -> entity.getType() != TOKEN && entity.getType() != SCHEDULE)
                .map(entity -> {
                    final var account = accountFromEntity(entity, timestamp);
                    // Associate the account alias with this entity in the cache, if any.
                    if (account.alias().length() > 0) {
                        aliasedAccountCacheManager.putAccountAlias(account.alias(), key);
                    }
                    return account;
                })
                .or(() -> getDummySystemAccountIfApplicable(key))
                .orElse(null);
    }

    /**
     * In case a system account doesn't exist, in a historical contract
     * call for example, return a dummy account to avoid errors like
     * "Non-zero net hbar change when handling body"
     */
    private Optional<Account> getDummySystemAccountIfApplicable(AccountID accountID) {
        if (accountID != null && accountID.hasAccountNum()) {
            final var accountNum = accountID.accountNum();
            return AccountDetector.isStrictSystem(accountNum) && accountNum != 0
                    ? Optional.of(Account.newBuilder().accountId(accountID).build())
                    : Optional.empty();
        }
        return Optional.empty();
    }
}
