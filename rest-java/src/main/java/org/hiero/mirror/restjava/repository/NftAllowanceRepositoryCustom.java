// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.restjava.dto.NftAllowanceRequest;

public interface NftAllowanceRepositoryCustom extends JooqRepository {

    /**
     * Find all NftAllowance matching the request parameters with the given limit, sort order, and byOwner flag
     *
     * @param request
     * @param id
     * @return The matching nft allowances
     */
    @NotNull
    Collection<NftAllowance> findAll(NftAllowanceRequest request, EntityId id);
}
