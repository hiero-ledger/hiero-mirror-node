// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.repository;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenAirdrop;
import com.hedera.mirror.restjava.dto.TokenAirdropRequest;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;

public interface TokenAirdropRepositoryCustom extends JooqRepository {

    @NotNull
    Collection<TokenAirdrop> findAll(TokenAirdropRequest request, EntityId accountId);
}
