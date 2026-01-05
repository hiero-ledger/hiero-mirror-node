// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import org.hiero.mirror.restjava.dto.NetworkSupply;

public interface EntityRepositoryCustom {
    NetworkSupply getSupply(String whereClause);
}
