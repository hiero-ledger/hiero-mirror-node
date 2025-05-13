// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.annotation.Nonnull;
import org.hiero.mirror.restjava.common.EntityIdParameter;

public interface EntityService {

    Entity findById(@Nonnull EntityId id);

    EntityId lookup(@Nonnull EntityIdParameter entityId);
}
