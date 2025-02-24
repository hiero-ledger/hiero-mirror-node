// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common.domain.entity;

import com.hedera.mirror.common.domain.History;

public interface FungibleAllowance extends History {

    long getAmount();

    void setAmount(long amount);
}
