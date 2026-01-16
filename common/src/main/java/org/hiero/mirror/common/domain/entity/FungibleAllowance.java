// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.entity;

import org.hiero.mirror.common.domain.History;

public interface FungibleAllowance extends History {

    long getAmount();

    void setAmount(long amount);
}
