// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository.projections;

public interface TokenAccountAssociationsCount {

    Integer getTokenCount();

    boolean getIsPositiveBalance();
}
