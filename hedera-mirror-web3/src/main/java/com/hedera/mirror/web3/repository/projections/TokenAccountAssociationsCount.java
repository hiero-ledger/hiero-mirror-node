// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.repository.projections;

public interface TokenAccountAssociationsCount {

    Integer getTokenCount();

    boolean getIsPositiveBalance();
}
