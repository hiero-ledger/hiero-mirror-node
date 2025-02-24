// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.service;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.restjava.common.EntityIdParameter;
import jakarta.annotation.Nonnull;

public interface EntityService {

    Entity findById(@Nonnull EntityId id);

    EntityId lookup(@Nonnull EntityIdParameter entityId);
}
