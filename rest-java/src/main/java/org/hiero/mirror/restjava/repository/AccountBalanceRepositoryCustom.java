// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import org.hiero.mirror.restjava.dto.NetworkSupply;

public interface AccountBalanceRepositoryCustom {
    NetworkSupply getSupplyHistory(String whereClause, long lowerTimestamp, long upperTimestamp);
}
