// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.ACCOUNTS_STATE_ID;
import static org.hiero.mirror.common.util.DomainUtils.toEvmAddress;
import static org.hiero.mirror.web3.state.Utils.ZERO_ADDRESS;
import static org.hiero.mirror.web3.state.Utils.hexStringToLong;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.viewmodel.StateOverride;

/** Applies account state overrides consistently to direct and alias-resolved account lookups. */
@UtilityClass
public class AccountStateOverrideApplier {

    /**
     * Applies {@code balance}, {@code nonce}, and {@code code} state overrides (if any) to an account before it enters the
     * request-local read cache. Storage-only overrides ({@code state}/{@code stateDiff}) are applied separately via
     * {@code ContractStorageReadableKVState} and must not mark a delegated EOA as a smart contract, so HIP-1340
     * delegation-proxy resolution is preserved.
     * <p>
     * When the account does not exist in the DB but an override is present, a synthetic account is created so
     * that the EVM can execute against the overridden state, and is seeded in the WritableKVState write
     * cache (via {@link org.hiero.mirror.web3.common.ContractCallContext#getWriteCacheState}) so that
     * subsequent {@code WritableKVState.get()} lookups can find it within the same request. Existing
     * write-cache entries, including deletion markers, are preserved.
     *
     * @param account the account loaded from the DB, or {@code null} when no DB record exists
     * @param key     the {@link AccountID} used for the lookup; must be non-null. It is the key under which the
     *                (possibly synthetic) account is stored in the write cache
     */
    public static Account apply(final ContractCallContext context, final Account account, final AccountID key) {
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
        // Only a bytecode override should mark the account as a smart contract. Storage-only
        // overrides must not flip a delegated EOA to a contract, or the EIP-7702 / HIP-1340
        // delegation proxy is skipped and the call executes no delegated code.
        final var hasCodeOverride = stateOverride.getCode() != null;
        final var hasStorageOverride = isNonEmpty(stateOverride.getState()) || isNonEmpty(stateOverride.getStateDiff());
        final var isDelegatedAccount = account != null
                && account.delegationAddress().length() > 0
                && !Arrays.equals(account.delegationAddress().toByteArray(), ZERO_ADDRESS);
        final var isSmartContract = hasCodeOverride || (hasStorageOverride && !isDelegatedAccount);

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

        // Overrides establish the initial state; simulated updates and deletions take precedence.
        final var writeCache = context.getWriteCacheState(ACCOUNTS_STATE_ID);
        if (!writeCache.containsKey(key)) {
            writeCache.put(key, result);
        }
        return result;
    }

    private static boolean isNonEmpty(final List<?> list) {
        return list != null && !list.isEmpty();
    }
}
