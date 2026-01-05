// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.restjava.dto.NetworkSupply;

@Named
@RequiredArgsConstructor
final class EntityRepositoryCustomImpl implements EntityRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public NetworkSupply getSupply(String whereClause) {
        final var queryString = String.format(
                """
                select coalesce(sum(balance), 0) as unreleased_supply,
                       coalesce(max(balance_timestamp), 0) as consensus_timestamp
                from entity
                where %s
                """,
                whereClause);
        final var query = entityManager.createNativeQuery(queryString);
        final var result = (Object[]) query.getSingleResult();
        return new NetworkSupply(((Number) result[0]).longValue(), ((Number) result[1]).longValue());
    }
}
