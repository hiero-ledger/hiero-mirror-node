// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import jakarta.validation.constraints.NotNull;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.hiero.mirror.restjava.service.Bound;

public interface NetworkSupplyRepositoryCustom extends JooqRepository {

    /**
     * Get the network supply at the latest timestamp or at a specific timestamp if provided.
     *
     * @param timestamp The timestamp bound for the query
     * @return The network supply
     */
    @NotNull
    NetworkSupply getSupply(Bound timestamp);
}
