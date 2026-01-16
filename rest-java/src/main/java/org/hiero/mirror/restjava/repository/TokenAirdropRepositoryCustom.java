// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenAirdrop;
import org.hiero.mirror.restjava.dto.TokenAirdropRequest;

public interface TokenAirdropRepositoryCustom extends JooqRepository {

    @NotNull
    Collection<TokenAirdrop> findAll(TokenAirdropRequest request, EntityId accountId);
}
