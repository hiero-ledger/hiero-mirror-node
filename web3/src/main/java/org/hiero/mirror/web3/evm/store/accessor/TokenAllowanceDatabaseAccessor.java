// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store.accessor;

import jakarta.inject.Named;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.AbstractTokenAllowance;
import org.hiero.mirror.common.domain.entity.TokenAllowance;
import org.hiero.mirror.web3.evm.store.DatabaseBackedStateFrame.DatabaseAccessIncorrectKeyTypeException;
import org.hiero.mirror.web3.repository.TokenAllowanceRepository;
import org.jspecify.annotations.NonNull;

@Named
@RequiredArgsConstructor
public class TokenAllowanceDatabaseAccessor extends DatabaseAccessor<Object, TokenAllowance> {

    private final TokenAllowanceRepository tokenAllowanceRepository;

    @Override
    public @NonNull Optional<TokenAllowance> get(@NonNull Object key, final Optional<Long> timestamp) {
        if (key instanceof AbstractTokenAllowance.Id id) {
            return timestamp
                    .map(t -> tokenAllowanceRepository.findByOwnerSpenderTokenAndTimestamp(
                            id.getOwner(), id.getSpender(), id.getTokenId(), t))
                    .orElseGet(() -> tokenAllowanceRepository.findById(id));
        }
        throw new DatabaseAccessIncorrectKeyTypeException("Accessor for class %s failed to fetch by key of type %s"
                .formatted(TokenAllowance.class.getTypeName(), key.getClass().getTypeName()));
    }
}
