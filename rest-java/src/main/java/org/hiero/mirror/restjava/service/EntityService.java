// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.common.EntityIdParameter;
import org.jspecify.annotations.NonNull;

public interface EntityService {

    Entity findById(@NonNull EntityId id);

    EntityId lookup(@NonNull EntityIdParameter entityId);
}
