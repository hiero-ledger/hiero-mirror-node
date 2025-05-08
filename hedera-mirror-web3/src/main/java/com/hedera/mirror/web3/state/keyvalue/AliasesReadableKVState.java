// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.keyvalue;

import static com.hedera.services.utils.EntityIdUtils.toAccountId;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.mirror.common.domain.SystemEntity;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.repository.AccountBalanceRepository;
import com.hedera.mirror.web3.repository.CryptoAllowanceRepository;
import com.hedera.mirror.web3.repository.NftAllowanceRepository;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import com.hedera.mirror.web3.repository.TokenAllowanceRepository;
import com.hedera.mirror.web3.state.CommonEntityAccessor;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;

@Named
public class AliasesReadableKVState extends AbstractAliasedAccountReadableKVState<ProtoBytes, AccountID> {

    public static final String KEY = "ALIASES";
    private final CommonEntityAccessor commonEntityAccessor;

    protected AliasesReadableKVState(
            final CommonEntityAccessor commonEntityAccessor,
            @Nonnull NftAllowanceRepository nftAllowanceRepository,
            @Nonnull NftRepository nftRepository,
            @Nonnull SystemEntity systemEntity,
            @Nonnull TokenAllowanceRepository tokenAllowanceRepository,
            @Nonnull CryptoAllowanceRepository cryptoAllowanceRepository,
            @Nonnull TokenAccountRepository tokenAccountRepository,
            @Nonnull AccountBalanceRepository accountBalanceRepository,
            @Nonnull MirrorNodeEvmProperties mirrorNodeEvmProperties) {
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
    }

    @Override
    protected AccountID readFromDataSource(@Nonnull ProtoBytes alias) {
        final var timestamp = ContractCallContext.get().getTimestamp();
        final var entity = commonEntityAccessor.get(alias.value(), timestamp);
        return entity.map(e -> {
                    final var accountID = toAccountId(e.getShard(), e.getRealm(), e.getNum());
                    final var account = accountFromEntity(e, timestamp);
                    // Put the account in the account num cache.
                    putAccountNumInCache(accountID, account);
                    return accountID;
                })
                .orElse(null);
    }

    private void putAccountNumInCache(final AccountID accountID, final Account account) {
        getReadCache(AccountReadableKVState.KEY).putIfAbsent(accountID, account);
    }
}
