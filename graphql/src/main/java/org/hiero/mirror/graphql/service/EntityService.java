// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.service;

import java.util.Optional;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;

public interface EntityService {

    Optional<Entity> getByIdAndType(EntityId entityId, EntityType type);

    Optional<Entity> getByAliasAndType(String alias, EntityType type);

    Optional<Entity> getByEvmAddressAndType(String evmAddress, EntityType type);
}
