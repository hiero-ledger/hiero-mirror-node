// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.accessor;

import jakarta.inject.Named;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.token.AbstractTokenAccount;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.web3.evm.store.DatabaseBackedStateFrame.DatabaseAccessIncorrectKeyTypeException;
import org.hiero.mirror.web3.repository.TokenAccountRepository;
import org.jspecify.annotations.NonNull;

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
