// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.parameter.EntityIdParameter;

public interface EntityService {

    Entity findById(EntityId id);

    EntityId lookup(EntityIdParameter entityId);
}
