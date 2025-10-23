// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.CustomFee;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface CustomFeeService {

    CustomFee findById(EntityId id);
}
