// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.Token;
import org.jspecify.annotations.Nullable;
import org.springframework.data.repository.CrudRepository;

public interface TokenRepository extends CrudRepository<Token, Long> {

    // A projection avoids cached JPA entities becoming stale when the parser flushes token state through JDBC.
    @Nullable
    Treasury findTreasuryByTokenId(long tokenId);

    record Treasury(EntityId treasuryAccountId, byte[] freezeKey, byte[] kycKey) {}
}
