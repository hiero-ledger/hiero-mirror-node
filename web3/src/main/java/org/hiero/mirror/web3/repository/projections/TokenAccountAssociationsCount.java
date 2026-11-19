// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository.projections;

import lombok.Value;

/** Query projection for token association counts grouped by balance sign. */
@Value
public class TokenAccountAssociationsCount {

    Integer tokenCount;

    boolean isPositiveBalance;
}
