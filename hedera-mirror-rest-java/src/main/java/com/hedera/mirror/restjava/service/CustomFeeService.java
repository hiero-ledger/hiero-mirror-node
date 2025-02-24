// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.service;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.CustomFee;
import jakarta.annotation.Nonnull;

public interface CustomFeeService {

    CustomFee findById(@Nonnull EntityId id);
}
