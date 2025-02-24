// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.repository;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.restjava.dto.NftAllowanceRequest;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;

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
