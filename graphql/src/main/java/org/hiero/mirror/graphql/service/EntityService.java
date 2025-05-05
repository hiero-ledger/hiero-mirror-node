// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.service;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import java.util.Optional;

public interface EntityService {

    Optional<Entity> getByIdAndType(EntityId entityId, EntityType type);

    Optional<Entity> getByAliasAndType(String alias, EntityType type);

    Optional<Entity> getByEvmAddressAndType(String evmAddress, EntityType type);
}
