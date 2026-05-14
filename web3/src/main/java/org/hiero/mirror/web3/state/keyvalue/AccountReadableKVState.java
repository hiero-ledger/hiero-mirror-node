// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.ACCOUNTS_STATE_ID;
import static org.hiero.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.inject.Named;
import java.math.BigInteger;
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

        final var timestamp = ContractCallContext.get().getTimestamp();
        final var account = commonEntityAccessor
                .get(key, timestamp)
                .filter(entity -> entity.getType() == ACCOUNT || entity.getType() == CONTRACT)
                .map(entity -> {
                    final var acc = accountFromEntity(entity, timestamp);
                    // Associate the account alias with this entity in the cache, if any.
                    if (acc.alias().length() > 0) {
                        aliasedAccountCacheManager.putAccountAlias(acc.alias(), key);
                    }
                    return acc;
                })
                .or(() -> getDummySystemAccountIfApplicable(key))
                .orElse(null);

        return applyAccountStateOverride(key, account);
    }

    /**
     * Applies {@code balance} and {@code nonce} state overrides (if any) to the account fetched from the DB.
     * When the account does not exist in the DB but an override is present, a synthetic account is created so
     * that the EVM can execute against the overridden state.
     */
    private Account applyAccountStateOverride(@NonNull AccountID key, Account account) {
        final var overrides = ContractCallContext.get().getStateOverrides();
        if (overrides.isEmpty()) {
            return account;
        }

        final var evmAddr = accountIdToEvmAddressHex(key);
        if (evmAddr == null) {
            return account;
        }

        final var override = overrides.get(evmAddr);
        if (override == null || (override.getBalance() == null && override.getNonce() == null)) {
            return account;
        }

        if (account == null) {
            // Create a minimal synthetic account so the EVM can use the overridden state.
            return Account.newBuilder()
                    .accountId(key)
                    .key(getDefaultKey())
                    .tinybarBalance(override.getBalance() != null ? parseHexBalance(override.getBalance()) : 0L)
                    .ethereumNonce(override.getNonce() != null ? override.getNonce() : 0L)
                    .build();
        }

        final var builder = account.copyBuilder();
        if (override.getBalance() != null) {
            builder.tinybarBalance(parseHexBalance(override.getBalance()));
        }
        if (override.getNonce() != null) {
            builder.ethereumNonce(override.getNonce());
        }
        return builder.build();
    }

    /**
     * Returns the normalized ({@code 0x}-prefixed, lowercase) EVM address for the given {@link AccountID}.
     * <ul>
     *   <li>If the ID has an alias that is exactly 20 bytes it IS the EVM address.</li>
     *   <li>Otherwise the long-zero address is derived from the account number.</li>
     * </ul>
     */
    private static String accountIdToEvmAddressHex(@NonNull AccountID key) {
        if (key.hasAlias() && key.alias().length() == 20) {
            return "0x" + key.alias().toHex().toLowerCase();
        } else if (key.hasAccountNum()) {
            return "0x" + String.format("%040x", key.accountNum());
        }
        return null;
    }

    /** Parses a hex-encoded balance string (with or without {@code 0x} prefix) into tinybars, clamped to {@link Long#MAX_VALUE}. */
    private static long parseHexBalance(@NonNull String hexBalance) {
        final String hex =
                hexBalance.startsWith("0x") || hexBalance.startsWith("0X") ? hexBalance.substring(2) : hexBalance;
        if (hex.isEmpty()) {
            return 0L;
        }
        try {
            final var bigInt = new BigInteger(hex, 16);
            return bigInt.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : bigInt.longValue();
        } catch (NumberFormatException e) {
            return 0L;
        }
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
