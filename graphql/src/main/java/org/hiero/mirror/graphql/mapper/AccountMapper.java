// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.mapper;

import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.graphql.viewmodel.Account;
import org.mapstruct.Mapper;

@Mapper(config = EntityMapper.class)
public interface AccountMapper {
    Account map(Entity source);
}
