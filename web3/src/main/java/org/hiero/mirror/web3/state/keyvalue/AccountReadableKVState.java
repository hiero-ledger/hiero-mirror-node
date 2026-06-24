// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.ACCOUNTS_STATE_ID;
import static org.hiero.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.hiero.mirror.web3.state.Utils.hexStringToLong;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.inject.Named;
import java.util.Optional;
import java.util.Set;
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
import org.hiero.mirror.web3.utils.AccountDetector;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.jspecify.annotations.NonNull;

/**
 * This class serves as a repository layer between hedera app services read only state and the Postgres database in
 * mirror-node
 * <p>
 * The object, which is read from DB is converted to the PBJ generated format, so that it can properly be utilized by
 * the hedera app components
 *
 */
@Named
public class AccountReadableKVState extends AbstractAliasedAccountReadableKVState<AccountID, Account> {

    public static final int STATE_ID = ACCOUNTS_STATE_ID;

    private final CommonEntityAccessor commonEntityAccessor;
    private final AliasedAccountCacheManager aliasedAccountCacheManager;
    private final Set<AccountID> systemAccounts;

    public AccountReadableKVState(
            @NonNull CommonEntityAccessor commonEntityAccessor,
            @NonNull NftAllowanceRepository nftAllowanceRepository,
            @NonNull NftRepository nftRepository,
            @NonNull SystemEntity systemEntity,
            @NonNull TokenAllowanceRepository tokenAllowanceRepository,
            @NonNull CryptoAllowanceRepository cryptoAllowanceRepository,
            @NonNull TokenAccountRepository tokenAccountRepository,
            @NonNull AccountBalanceRepository accountBalanceRepository,
            @NonNull EvmProperties evmProperties,
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
                evmProperties);
        this.commonEntityAccessor = commonEntityAccessor;
        this.aliasedAccountCacheManager = aliasedAccountCacheManager;
        this.systemAccounts = Set.of(
                EntityIdUtils.toAccountId(systemEntity.feeCollectionAccount()),
                EntityIdUtils.toAccountId(systemEntity.networkAdminFeeAccount()),
                EntityIdUtils.toAccountId(systemEntity.nodeRewardAccount()),
                EntityIdUtils.toAccountId(systemEntity.stakingRewardAccount()));
    }

    @Override
    protected Account readFromDataSource(@NonNull AccountID key) {
        if (!ContractCallContext.isBalanceCallSafe() && systemAccounts.contains(key)) {
            return getDummySystemAccountIfApplicable(key).orElse(null);
        }

        final var context = ContractCallContext.get();
        final var timestamp = context.getTimestamp();
        return commonEntityAccessor
                .get(key, timestamp)
                .filter(entity -> entity.getType() == ACCOUNT || entity.getType() == CONTRACT)
                .map(entity -> {
                    final var account = accountFromEntity(entity, timestamp);
                    // Associate the account alias with this entity in the cache, if any.
                    if (account.alias().length() > 0) {
                        aliasedAccountCacheManager.putAccountAlias(account.alias(), key);
                    }
                    return account;
                })
                .or(() -> getDummySystemAccountIfApplicable(key))
                .map(account -> applyStateOverride(context, account, key))
                .orElseGet(() -> applyStateOverride(context, null, key));
    }

    /**
     * Applies {@code balance} and {@code nonce} state overrides (if any) to the account fetched from the DB.
     * When the account does not exist in the DB but an override is present, a synthetic account is created so
     * that the EVM can execute against the overridden state, and is persisted in the WritableKVState write
     * cache (via {@link org.hiero.mirror.web3.common.ContractCallContext#getWriteCacheState}) so that
     * subsequent {@code WritableKVState.get()} lookups can find it within the same request.
     *
     * @param account the account loaded from the DB, or {@code null} when no DB record exists
     * @param key     the {@link AccountID} used for the lookup; must be non-null. It is the key under which the
     *                (possibly synthetic) account is stored in the write cache
     */
    private Account applyStateOverride(final ContractCallContext context, final Account account, final AccountID key) {
        final var overrides = context.getStateOverrides();
        if (overrides == null || overrides.isEmpty()) {
            return account;
        }

        Bytes accountAddress = null;
        StateOverride stateOverride = null;
        if (account != null) {
            if (ConversionUtils.isEvmAddress(account.alias())) {
                accountAddress = account.alias();
            } else if (account.accountId() != null) {
                accountAddress = Bytes.wrap(toEvmAddress(account.accountId().accountNum()));
            }
        } else if (key != null) {
            // Derive the EVM address from the lookup key so we can resolve the override
            // for an account that has no DB record yet.
            if (key.hasAlias() && ConversionUtils.isEvmAddress(key.alias())) {
                accountAddress = key.alias();
            } else if (key.hasAccountNum()) {
                accountAddress = Bytes.wrap(toEvmAddress(key.accountNum()));
            }
        }

        if (accountAddress != null) {
            stateOverride = overrides.get(accountAddress);
        }

        if (stateOverride == null || stateOverride.getAddress().isEmpty()) {
            return account;
        }

        final var hasBalance = stateOverride.getBalance() != null;
        final var hasNonce = stateOverride.getNonce() != null;
        final var isSmartContract = stateOverride.getCode() != null
                || stateOverride.getState() != null
                || stateOverride.getStateDiff() != null;

        Account.Builder builder;
        if (account == null) {
            builder = Account.newBuilder().accountId(key).alias(accountAddress);
        } else {
            builder = account.copyBuilder();
        }

        if (hasBalance) {
            builder.tinybarBalance(hexStringToLong(stateOverride.getBalance()));
        }
        if (hasNonce) {
            builder.ethereumNonce(hexStringToLong(stateOverride.getNonce()));
        }
        if (isSmartContract) {
            builder.smartContract(true);
        }

        final var result = builder.build();

        context.getWriteCacheState(STATE_ID).put(key, result);
        return result;
    }

    /**
     * In case a system account doesn't exist, in a historical contract call for example, return a dummy account to
     * avoid errors like "Non-zero net hbar change when handling body"
     */
    private Optional<Account> getDummySystemAccountIfApplicable(AccountID accountID) {
        if (accountID != null && accountID.hasAccountNum()) {
            final var accountNum = accountID.accountNum();
            return AccountDetector.isStrictSystem(accountNum) && accountNum != 0
                    ? Optional.of(Account.newBuilder()
                            .accountId(accountID)
                            .key(getDefaultKey())
                            .build())
                    : Optional.empty();
        }
        return Optional.empty();
    }

    @Override
    public String getServiceName() {
        return TokenService.NAME;
    }
}
