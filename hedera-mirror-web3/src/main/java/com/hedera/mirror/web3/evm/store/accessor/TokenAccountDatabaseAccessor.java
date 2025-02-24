// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.store.accessor;

import com.hedera.mirror.common.domain.token.AbstractTokenAccount;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.web3.evm.store.DatabaseBackedStateFrame.DatabaseAccessIncorrectKeyTypeException;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import jakarta.inject.Named;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class TokenAccountDatabaseAccessor extends DatabaseAccessor<Object, TokenAccount> {

    private final TokenAccountRepository tokenAccountRepository;

    @Override
    public @NonNull Optional<TokenAccount> get(@NonNull Object key, final Optional<Long> timestamp) {
        if (key instanceof AbstractTokenAccount.Id id) {
            return timestamp
                    .map(t -> tokenAccountRepository.findByIdAndTimestamp(id.getAccountId(), id.getTokenId(), t))
                    .orElseGet(() -> tokenAccountRepository.findById(id));
        }
        throw new DatabaseAccessIncorrectKeyTypeException("Accessor for class %s failed to fetch by key of type %s"
                .formatted(TokenAccount.class.getTypeName(), key.getClass().getTypeName()));
    }
}
