// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.CustomFee;

public interface CustomFeeService {

    CustomFee findById(EntityId id);

    Optional<CustomFee> findByIdOptional(EntityId id);
}
